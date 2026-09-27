package com.hhxy.huazi.memory.service;

import com.hhxy.huazi.memory.model.CommitResult;
import com.hhxy.huazi.memory.model.MemoryScope;
import com.hhxy.huazi.memory.model.MemorySnapshot;
import com.hhxy.huazi.memory.model.MemoryTurn;
import com.hhxy.huazi.memory.model.MemoryValidationException;
import com.hhxy.huazi.memory.policy.TurnWindowPolicy;
import com.hhxy.huazi.memory.store.MemoryStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** 只接收完整轮次，依靠快照版本处理并发提交；不执行模型生成，也不自动重放业务。 */
public final class ConversationMemoryService {
    private final MemoryStore store;
    private final TurnWindowPolicy policy;
    private final MeterRegistry metrics;

    public ConversationMemoryService(MemoryStore store, TurnWindowPolicy policy, MeterRegistry metrics) {
        this.store = Objects.requireNonNull(store, "记忆存储不能为空");
        this.policy = Objects.requireNonNull(policy, "轮次窗口策略不能为空");
        this.metrics = Objects.requireNonNull(metrics, "指标注册表不能为空");
    }

    /** 返回的快照版本用于后续提交；会话已存在时不重置内容或延长保留期。 */
    public MemoryStore.LoadResult loadOrCreate(MemoryScope scope) {
        Objects.requireNonNull(scope, "记忆作用域不能为空");
        return timed("load", () -> store.loadOrCreate(scope));
    }

    /** 空结果仅表示会话不存在或已过期；存储故障以异常报告，读取不改变会话生命周期。 */
    public Optional<MemorySnapshot> read(MemoryScope scope) {
        Objects.requireNonNull(scope, "记忆作用域不能为空");
        return timed("read", () -> store.load(scope));
    }

    /** CONFIRMED 表示窗口内内容匹配；NOT_RETAINED 无法证明从未提交；SESSION_EXPIRED 表示会话不存在。 */
    public enum Verification { CONFIRMED, NOT_RETAINED, SESSION_EXPIRED }

    /** 只核对当前保留窗口，不创建或续期；同一请求标识对应不同内容时抛出校验异常。 */
    public Verification verifyCommit(MemoryScope scope, MemoryTurn turn) {
        Objects.requireNonNull(scope, "记忆作用域不能为空");
        policy.validateTurn(turn);
        return timed("verify", () -> {
            var snapshot = store.load(scope);
            if (snapshot.isEmpty()) {
                return Verification.SESSION_EXPIRED;
            }
            for (MemoryTurn retained : snapshot.get().turns()) {
                if (retained.requestId().equals(turn.requestId())) {
                    checkDigest(policy.digest(retained), policy.digest(turn));
                    return Verification.CONFIRMED;
                }
            }
            return Verification.NOT_RETAINED;
        });
    }

    /** expectedRevision 来自生成前的快照；提交结果未知时应核验原轮次，不重新生成消息或盲目重放。 */
    public CommitResult commitTurn(MemoryScope scope, String expectedRevision, MemoryTurn turn) {
        Objects.requireNonNull(scope, "记忆作用域不能为空");
        MemorySnapshot.requireRevision(expectedRevision);
        policy.validateTurn(turn);
        return timed("commit", () -> recordCommit(commit(scope, expectedRevision, turn)));
    }

    /** 清空通过新版本使旧请求失效；返回结果未知时不能承诺内容已被清空。 */
    public CommitResult clear(MemoryScope scope) {
        Objects.requireNonNull(scope, "记忆作用域不能为空");
        return timed("clear", () -> {
            CommitResult result = store.clear(scope);
            if (result.status() == CommitResult.Status.OUTCOME_UNKNOWN) {
                metrics.counter("netagent.memory.clear.unknown").increment();
            }
            return result;
        });
    }

    /** 先核验当前窗口的幂等记录，再生成裁剪快照；预读只做快速判断，最终以存储层原子结果为准。 */
    private CommitResult commit(MemoryScope scope, String expectedRevision, MemoryTurn turn) {
        var loaded = store.load(scope);
        if (loaded.isEmpty()) {
            return CommitResult.of(CommitResult.Status.SESSION_EXPIRED);
        }
        MemorySnapshot current = loaded.get();
        String digest = policy.digest(turn);
        // 先核验重复提交再比较版本，使响应丢失后的同内容重试可被识别。
        if (current.lastCommitId().equals(turn.requestId())) {
            checkDigest(current.lastCommitDigest(), digest);
            return new CommitResult(CommitResult.Status.ALREADY_COMMITTED, current.revision());
        }
        // 历史请求虽仍保留但非最近提交，保守返回未知而不重放。
        for (MemoryTurn previous : current.turns()) {
            if (previous.requestId().equals(turn.requestId())) {
                checkDigest(policy.digest(previous), digest);
                return CommitResult.of(CommitResult.Status.OUTCOME_UNKNOWN);
            }
        }
        if (!current.revision().equals(expectedRevision)) {
            return CommitResult.of(CommitResult.Status.CONFLICT);
        }
        MemorySnapshot next = policy.append(current, turn, UUID.randomUUID().toString(), Instant.now());
        metrics.summary("netagent.memory.payload.bytes").record(policy.snapshotBytes(next));
        // 预读后的并发变化仍由存储层原子比较版本拦截。
        return store.compareAndSet(scope, expectedRevision, next);
    }

    /** 同一请求标识必须绑定同一份完整轮次，内容变化不是可接受的幂等重试。 */
    private void checkDigest(String previous, String next) {
        if (!previous.equals(next)) {
            throw new MemoryValidationException("同一请求标识不能用于不同的内容");
        }
    }

    /** 冲突与结果未知分别统计，避免将并发拒绝和通信不确定性混为同类故障。 */
    private CommitResult recordCommit(CommitResult result) {
        if (result.status() == CommitResult.Status.CONFLICT) {
            metrics.counter("netagent.memory.commit.conflicts").increment();
        } else if (result.status() == CommitResult.Status.OUTCOME_UNKNOWN) {
            metrics.counter("netagent.memory.commit.unknown").increment();
        }
        return result;
    }

    /** 正常返回和抛出异常都记录耗时；操作标签只使用固定名称，不加入会话标识。 */
    private <T> T timed(String operation, Supplier<T> action) {
        Timer.Sample sample = Timer.start(metrics);
        try {
            return action.get();
        } finally {
            sample.stop(metrics.timer("netagent.memory.operation.duration", "operation", operation));
        }
    }
}
