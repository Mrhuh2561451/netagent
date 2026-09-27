package com.hhxy.huazi.memory.lock;

import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 同步短临界区使用 Redisson watchdog，不设置固定租期。
 * 锁不是事务或 fencing token；网络分区、进程暂停仍可能导致失锁。
 * 不应返回未完成的异步任务，也不应用于长时间模型调用或流式响应。
 */
public final class DistributedLockExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(DistributedLockExecutor.class);
    private final RedissonClient client;
    private final MeterRegistry meters;

    public DistributedLockExecutor(RedissonClient client, MeterRegistry meters) {
        this.client = Objects.requireNonNull(client, "Redis 客户端不能为空");
        this.meters = Objects.requireNonNull(meters, "指标注册表不能为空");
    }

    /** 在获锁线程同步执行业务，业务异常原样传播，不转换成锁不可用。 */
    @FunctionalInterface
    public interface Work<T> {
        /** 可返回空值；退出方法即开始释放锁，不可用未完成的异步结果延长临界区。 */
        T execute() throws Exception;
    }

    // NOT_ACQUIRED 表示未获锁；RELEASED 表示已释放；LOST 表示解锁时已无所有权；FAILED 表示解锁失败、无法确认释放。
    public enum ReleaseStatus { NOT_ACQUIRED, RELEASED, LOST, FAILED }

    // acquired 表示曾经获锁，不表示返回时仍持有锁；业务抛出异常时不会生成 Execution。
    /** 获锁后的空结果有效；释放失败通过状态报告，不丢弃已完成的结果。 */
    public record Execution<T>(boolean acquired, T value, ReleaseStatus releaseStatus) {
        public Execution {
            Objects.requireNonNull(releaseStatus, "锁释放状态不能为空");
            if (acquired == (releaseStatus == ReleaseStatus.NOT_ACQUIRED) || (!acquired && value != null)) {
                throw new IllegalArgumentException("锁执行结果无效");
            }
        }

        /** 仅打印执行状态，不将业务返回值带入日志。 */
        @Override
        public String toString() {
            return "Execution[acquired=" + acquired + ", releaseStatus=" + releaseStatus + "]";
        }
    }

    /** wait 仅用于锁竞争等待，不是业务超时；acquired 为 false 时不执行回调，中断原样抛出且保留线程中断标记。 */
    public <T> Execution<T> tryExecute(String lockName, Duration wait, Work<T> work) throws Exception {
        if (lockName == null || lockName.isBlank()) {
            throw new IllegalArgumentException("锁名不能为空");
        }
        Objects.requireNonNull(work, "业务回调不能为空");
        Objects.requireNonNull(wait, "等待时长不能为空");
        if (wait.isNegative()) {
            throw new IllegalArgumentException("等待时长不能为负数");
        }
        final long waitNanos;
        try {
            waitNanos = wait.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("等待时长过大");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("获取锁前线程已被中断");
        }

        RLock lock;
        long started = System.nanoTime();
        // 仅获取阶段的运行时故障包装为 LockUnavailableException，竞争未获锁和线程中断另行报告。
        try {
            lock = client.getLock(lockName);
            // 明确使用两个参数的重载，不传入正数 leaseTime，以启用 watchdog。
            if (!lock.tryLock(waitNanos, TimeUnit.NANOSECONDS)) {
                recordDuration("netagent.lock.acquire.duration", "busy", started);
                return new Execution<>(false, null, ReleaseStatus.NOT_ACQUIRED);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordDuration("netagent.lock.acquire.duration", "interrupted", started);
            throw e;
        } catch (RuntimeException e) {
            recordDuration("netagent.lock.acquire.duration", "error", started);
            LOG.warn("获取分布式锁失败（{}）", e.getClass().getSimpleName());
            throw new LockUnavailableException();
        }
        recordDuration("netagent.lock.acquire.duration", "acquired", started);

        T value;
        ReleaseStatus release;
        long heldSince = System.nanoTime();
        String outcome = "error";
        // 业务阶段与获锁异常处理分离，即使业务抛出锁异常类型也保持原样，不重试回调。
        try {
            value = work.execute();
            outcome = "success";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = "interrupted";
            throw e;
        } finally {
            // 成功、失败或中断都尝试释放；常规解锁失败转为状态，不覆盖业务结果或原始异常。
            release = release(lock);
            recordDuration("netagent.lock.hold.duration", outcome, heldSince);
        }
        return new Execution<>(true, value, release);
    }

    // 释放状态仅反映本次解锁尝试，不能证明整个临界区期间始终拥有锁。
    private ReleaseStatus release(RLock lock) {
        ReleaseStatus status;
        // 临时清除中断，让解锁有机会执行，随后恢复中断状态。
        boolean interrupted = Thread.interrupted();
        try {
            // 普通解锁在 Redis 内检查线程所有权，避免前置查询失败阻断释放。
            lock.unlock();
            status = ReleaseStatus.RELEASED;
        } catch (IllegalMonitorStateException e) {
            status = ReleaseStatus.LOST;
            LOG.warn("释放分布式锁时所有权已丢失（{}）", e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            status = ReleaseStatus.FAILED;
            LOG.warn("释放分布式锁失败（{}）", e.getClass().getSimpleName());
        } finally {
            // 恢复调用方的取消信号，避免解锁吞掉中断。
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            meters.counter("netagent.lock.release", "outcome", status.name().toLowerCase(java.util.Locale.ROOT))
                    .increment();
        } catch (RuntimeException ignored) {
            // 观测失败不得覆盖已经获得的源结果或原始业务异常。
        }
        return status;
    }

    // 指标名和结果均来自固定调用点以保持低基数；观测失败不能改变获锁或业务结论。
    private void recordDuration(String name, String outcome, long started) {
        try {
            meters.timer(name, "outcome", outcome).record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            // 指标标签不包含调用方标识、锁名或异常原文。
        }
    }
}
