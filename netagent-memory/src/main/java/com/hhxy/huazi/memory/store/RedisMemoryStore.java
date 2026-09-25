package com.hhxy.huazi.memory.store;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhxy.huazi.memory.config.MemoryProperties;
import com.hhxy.huazi.memory.model.CommitResult;
import com.hhxy.huazi.memory.model.MemoryScope;
import com.hhxy.huazi.memory.model.MemorySnapshot;
import com.hhxy.huazi.memory.model.MemoryTurn;
import com.hhxy.huazi.memory.model.MemoryValidationException;
import com.hhxy.huazi.memory.policy.TurnWindowPolicy;
import com.hhxy.huazi.memory.support.MemoryJson;
import com.hhxy.huazi.memory.support.RedisKeyFactory;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.OptionalOptions;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.StringCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RedisMemoryStore implements MemoryStore {
    private static final String SCRIPT = readScript();
    private static final TypeReference<List<MemoryTurn>> TURNS = new TypeReference<>() { };
    private final RScript script;
    private final RedisKeyFactory keys;
    private final MemoryProperties properties;
    private final TurnWindowPolicy policy;
    private final ObjectMapper json = MemoryJson.createMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public RedisMemoryStore(RedissonClient client, RedisKeyFactory keys, MemoryProperties properties) {
        // 禁用自动重试，避免响应丢失后重放写入；由调用方核验提交结果。
        this.script = Objects.requireNonNull(client, "Redis 客户端不能为空").getScript(
                OptionalOptions.defaults().codec(StringCodec.INSTANCE).retryAttempts(0));
        this.keys = Objects.requireNonNull(keys, "Redis 键工厂不能为空");
        this.properties = Objects.requireNonNull(properties, "记忆配置不能为空");
        // Lua 数值使用双精度浮点表示，保留时长不能超出可精确表示的整数范围。
        if (properties.retention().toMillis() > 9_007_199_254_740_991L) {
            throw new MemoryValidationException("记忆保留时长超出支持范围");
        }
        this.policy = new TurnWindowPolicy(properties);
    }

    @Override
    public Optional<MemorySnapshot> load(MemoryScope scope) {
        List<Object> result = read("load", scope, null);
        if ("MISSING".equals(result.get(0))) {
            return Optional.empty();
        }
        return Optional.of(decode(result).snapshot());
    }

    @Override
    public LoadResult loadOrCreate(MemoryScope scope) {
        MemorySnapshot empty = MemorySnapshot.empty(UUID.randomUUID().toString(), Instant.now());
        policy.validateSnapshot(empty);
        return decode(read("init", scope, empty));
    }

    @Override
    public CommitResult compareAndSet(MemoryScope scope, String expectedRevision, MemorySnapshot replacement) {
        MemorySnapshot.requireRevision(expectedRevision);
        policy.validateSnapshot(replacement);
        if (replacement.turns().isEmpty() || replacement.revision().equals(expectedRevision)) {
            throw new MemoryValidationException("比较并交换提交需要新的版本标识和已完成的轮次");
        }
        return write("cas", scope, expectedRevision, replacement);
    }

    @Override
    public CommitResult clear(MemoryScope scope) {
        String expected = load(scope).map(MemorySnapshot::revision).orElse("");
        MemorySnapshot empty = MemorySnapshot.empty(UUID.randomUUID().toString(), Instant.now());
        policy.validateSnapshot(empty);
        return write("clear", scope, expected, empty);
    }

    private List<Object> read(String operation, MemoryScope scope, MemorySnapshot replacement) {
        try {
            return execute(operation, scope, "", replacement);
        } catch (RedisException ignored) {
            throw new MemoryUnavailableException();
        }
    }

    private CommitResult write(String operation, MemoryScope scope, String expected, MemorySnapshot replacement) {
        try {
            List<Object> result = execute(operation, scope, expected, replacement);
            CommitResult.Status status;
            try {
                status = CommitResult.Status.valueOf((String) result.get(0));
            } catch (RuntimeException ignored) {
                throw new MemoryFormatException();
            }
            String revision = result.size() == 2 ? (String) result.get(1) : null;
            return new CommitResult(status, revision);
        } catch (RedisTimeoutException | RedisConnectionException ignored) {
            // 连接异常不代表脚本未执行，不能把可能已完成的写入报告为明确失败。
            return CommitResult.of(CommitResult.Status.OUTCOME_UNKNOWN);
        } catch (RedisException ignored) {
            throw new MemoryUnavailableException();
        }
    }

    private List<Object> execute(String operation, MemoryScope scope, String expected, MemorySnapshot replacement) {
        Objects.requireNonNull(scope, "记忆作用域不能为空");
        String key = keys.memoryKey(scope.tenantId(), scope.userId(), scope.agentId(), scope.conversationId());
        // 参数顺序与 Lua 的 ARGV 保持一致，校验、版本比较和写入在单键脚本内原子完成。
        List<Object> result = script.eval(RScript.Mode.READ_WRITE, SCRIPT, RScript.ReturnType.LIST,
                List.of(key), operation, expected, replacement == null ? "" : replacement.revision(),
                replacement == null ? "" : policy.encode(replacement.turns()),
                replacement == null ? "" : replacement.updatedAt().toString(),
                replacement == null ? "" : replacement.lastCommitId(),
                replacement == null ? "" : replacement.lastCommitDigest(),
                Long.toString(properties.retention().toMillis()), Integer.toString(properties.maxSnapshotBytes()),
                Integer.toString(properties.maxTurns()), Integer.toString(properties.maxMessagesPerTurn()),
                Integer.toString(properties.maxTurnBytes()));
        if (result == null || result.isEmpty() || "FORMAT".equals(result.get(0))) {
            throw new MemoryFormatException();
        }
        if ("INVALID".equals(result.get(0))) {
            throw new MemoryValidationException("记忆脚本拒绝了无效的写入参数");
        }
        if ("IDENTITY_MISMATCH".equals(result.get(0))) {
            throw new MemoryValidationException("同一请求标识不能用于不同的内容");
        }
        return result;
    }

    private LoadResult decode(List<Object> result) {
        try {
            if (result.size() != 8 || !"STATE".equals(result.get(0))) {
                throw new MemoryFormatException();
            }
            String payload = (String) result.get(4);
            if (payload.getBytes(StandardCharsets.UTF_8).length > properties.maxSnapshotBytes()) {
                throw new MemoryFormatException();
            }
            MemorySnapshot snapshot = new MemorySnapshot(Integer.parseInt((String) result.get(2)),
                    (String) result.get(3), json.readValue(payload, TURNS), Instant.parse((String) result.get(5)),
                    (String) result.get(6), (String) result.get(7));
            policy.validateSnapshot(snapshot);
            return new LoadResult(snapshot, "1".equals(result.get(1)));
        } catch (Exception ignored) {
            throw new MemoryFormatException();
        }
    }

    private static String readScript() {
        try (var stream = RedisMemoryStore.class.getResourceAsStream("/com/hhxy/huazi/memory/memory.lua")) {
            if (stream == null) {
                throw new IllegalStateException("缺少记忆脚本资源");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            throw new IllegalStateException("无法加载记忆脚本资源");
        }
    }
}
