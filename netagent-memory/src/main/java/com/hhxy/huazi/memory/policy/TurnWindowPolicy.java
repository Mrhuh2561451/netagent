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

/** 同时约束轮次数和序列化字节数；只淘汰完整旧轮次，不截断消息或估算模型令牌数。 */
public final class TurnWindowPolicy {
    private final MemoryProperties properties;
    private final ObjectMapper json = MemoryJson.createMapper();

    public TurnWindowPolicy(MemoryProperties properties) {
        this.properties = Objects.requireNonNull(properties, "记忆配置不能为空");
    }

    /** 结构合法性由轮次模型保证；此处按包含工具过程的消息总数和实际 UTF-8 字节数拒绝超限轮次。 */
    public void validateTurn(MemoryTurn turn) {
        if (turn == null || turn.messages().size() > properties.maxMessagesPerTurn()) {
            throw new MemoryValidationException("轮次超出消息数量上限");
        }
        if (encode(turn).getBytes(StandardCharsets.UTF_8).length > properties.maxTurnBytes()) {
            throw new MemoryValidationException("轮次超出 UTF-8 字节数上限");
        }
    }

    /** 验证整份快照的窗口限额及末轮摘要，不通过裁剪来掩盖读取到的非法快照。 */
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

    /** 基于当前快照创建裁剪后的新快照，不改原对象；版本和时间由调用方提供，此处不执行持久化。 */
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
            // 新轮次必须完整保留，即使旧内容已全部淘汰，也不能靠截断新消息满足限额。
            if (turns.size() == 1) {
                throw new MemoryValidationException("新轮次无法容纳于快照字节数上限内");
            }
            turns.remove(0);
        }
    }

    /** 按完整快照计算 UTF-8 字节数，包含版本、时间和提交元数据，不只是消息文本长度。 */
    public int snapshotBytes(MemorySnapshot snapshot) {
        return encode(snapshot).getBytes(StandardCharsets.UTF_8).length;
    }

    /** 摘要覆盖请求标识、消息、工具参数及创建时间；核验重试必须复用同一轮次，而不只是同一回答。 */
    public String digest(MemoryTurn turn) {
        return RedisKeyFactory.digest(encode(turn));
    }

    /** 采用固定顺序的 JSON 同时服务于存储、限额和摘要，失败时不回显原始对话内容。 */
    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ignored) {
            throw new MemoryValidationException("记忆序列化失败");
        }
    }
}
