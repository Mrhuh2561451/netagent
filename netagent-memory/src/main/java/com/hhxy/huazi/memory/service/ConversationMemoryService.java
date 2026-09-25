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

public final class ConversationMemoryService {
    private final MemoryStore store;
    private final TurnWindowPolicy policy;
    private final MeterRegistry metrics;

    public ConversationMemoryService(MemoryStore store, TurnWindowPolicy policy, MeterRegistry metrics) {
        this.store = Objects.requireNonNull(store, "记忆存储不能为空");
        this.policy = Objects.requireNonNull(policy, "轮次窗口策略不能为空");
        this.metrics = Objects.requireNonNull(metrics, "指标注册表不能为空");
    }

    public MemoryStore.LoadResult loadOrCreate(MemoryScope scope) {
        Objects.requireNonNull(scope, "记忆作用域不能为空");
        return timed("load", () -> store.loadOrCreate(scope));
    }

    public Optional<MemorySnapshot> read(MemoryScope scope) {
        Objects.requireNonNull(scope, "记忆作用域不能为空");
        return timed("read", () -> store.load(scope));
    }

    public enum Verification { CONFIRMED, NOT_RETAINED, SESSION_EXPIRED }

    // 只核对当前保留窗口；未找到不代表从未提交，不创建会话或延长保留期。
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

    public CommitResult commitTurn(MemoryScope scope, String expectedRevision, MemoryTurn turn) {
        Objects.requireNonNull(scope, "记忆作用域不能为空");
        MemorySnapshot.requireRevision(expectedRevision);
        policy.validateTurn(turn);
        return timed("commit", () -> recordCommit(commit(scope, expectedRevision, turn)));
    }

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

    private void checkDigest(String previous, String next) {
        if (!previous.equals(next)) {
            throw new MemoryValidationException("同一请求标识不能用于不同的内容");
        }
    }

    private CommitResult recordCommit(CommitResult result) {
        if (result.status() == CommitResult.Status.CONFLICT) {
            metrics.counter("netagent.memory.commit.conflicts").increment();
        } else if (result.status() == CommitResult.Status.OUTCOME_UNKNOWN) {
            metrics.counter("netagent.memory.commit.unknown").increment();
        }
        return result;
    }

    private <T> T timed(String operation, Supplier<T> action) {
        Timer.Sample sample = Timer.start(metrics);
        try {
            return action.get();
        } finally {
            sample.stop(metrics.timer("netagent.memory.operation.duration", "operation", operation));
        }
    }
}
