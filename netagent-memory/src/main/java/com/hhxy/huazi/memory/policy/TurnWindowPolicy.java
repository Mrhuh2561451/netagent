package com.hhxy.huazi.memory.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhxy.huazi.memory.config.MemoryProperties;
import com.hhxy.huazi.memory.model.MemorySnapshot;
import com.hhxy.huazi.memory.model.MemoryTurn;
import com.hhxy.huazi.memory.model.MemoryValidationException;
import com.hhxy.huazi.memory.support.MemoryJson;
import com.hhxy.huazi.memory.support.RedisKeyFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;

public final class TurnWindowPolicy {
    private final MemoryProperties properties;
    private final ObjectMapper json = MemoryJson.createMapper();

    public TurnWindowPolicy(MemoryProperties properties) {
        this.properties = Objects.requireNonNull(properties, "记忆配置不能为空");
    }

    public void validateTurn(MemoryTurn turn) {
        if (turn == null || turn.messages().size() > properties.maxMessagesPerTurn()) {
            throw new MemoryValidationException("轮次超出消息数量上限");
        }
        if (encode(turn).getBytes(StandardCharsets.UTF_8).length > properties.maxTurnBytes()) {
            throw new MemoryValidationException("轮次超出 UTF-8 字节数上限");
        }
    }

    public void validateSnapshot(MemorySnapshot snapshot) {
        if (snapshot == null || snapshot.turns().size() > properties.maxTurns()
                || snapshotBytes(snapshot) > properties.maxSnapshotBytes()) {
            throw new MemoryValidationException("快照超出窗口限制");
        }
        snapshot.turns().forEach(this::validateTurn);
        // 将最后提交元数据与实际末轮绑定，防止错误去重。
        if (!snapshot.turns().isEmpty()
                && !digest(snapshot.turns().get(snapshot.turns().size() - 1)).equals(snapshot.lastCommitDigest())) {
            throw new MemoryValidationException("快照提交摘要不一致");
        }
    }

    public MemorySnapshot append(MemorySnapshot current, MemoryTurn turn, String revision, Instant now) {
        validateTurn(turn);
        var turns = new ArrayList<>(current.turns());
        turns.add(turn);
        String digest = digest(turn);
        // 按完整轮次淘汰最旧内容，不能截断工具调用链。
        while (true) {
            MemorySnapshot next = new MemorySnapshot(MemorySnapshot.SCHEMA_VERSION, revision,
                    turns, now, turn.requestId(), digest);
            if (turns.size() <= properties.maxTurns() && snapshotBytes(next) <= properties.maxSnapshotBytes()) {
                return next;
            }
            if (turns.size() == 1) {
                throw new MemoryValidationException("新轮次无法容纳于快照字节数上限内");
            }
            turns.remove(0);
        }
    }

    public int snapshotBytes(MemorySnapshot snapshot) {
        return encode(snapshot).getBytes(StandardCharsets.UTF_8).length;
    }

    public String digest(MemoryTurn turn) {
        return RedisKeyFactory.digest(encode(turn));
    }

    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ignored) {
            throw new MemoryValidationException("记忆序列化失败");
        }
    }
}
