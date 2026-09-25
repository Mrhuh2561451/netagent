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

    @FunctionalInterface
    public interface Work<T> {
        T execute() throws Exception;
    }

    public enum ReleaseStatus { NOT_ACQUIRED, RELEASED, LOST, FAILED }

    /** 获锁后的空结果有效；释放失败通过状态报告，不丢弃已完成的结果。 */
    public record Execution<T>(boolean acquired, T value, ReleaseStatus releaseStatus) {
        public Execution {
            Objects.requireNonNull(releaseStatus, "锁释放状态不能为空");
            if (acquired == (releaseStatus == ReleaseStatus.NOT_ACQUIRED) || (!acquired && value != null)) {
                throw new IllegalArgumentException("锁执行结果无效");
            }
        }

        @Override
        public String toString() {
            return "Execution[acquired=" + acquired + ", releaseStatus=" + releaseStatus + "]";
        }
    }

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
        try {
            value = work.execute();
            outcome = "success";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = "interrupted";
            throw e;
        } finally {
            release = release(lock);
            recordDuration("netagent.lock.hold.duration", outcome, heldSince);
        }
        return new Execution<>(true, value, release);
    }

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

    private void recordDuration(String name, String outcome, long started) {
        try {
            meters.timer(name, "outcome", outcome).record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            // 指标标签不包含调用方标识、锁名或异常原文。
        }
    }
}
