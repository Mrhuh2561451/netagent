package com.hhxy.huazi.memory.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 持久化记忆的保留期限与完整轮次窗口限制，不用于配置通用缓存的 TTL 或容量。 */
@ConfigurationProperties("netagent.memory")
public record MemoryProperties(
        // 保留时长仅在写入时续期，读取记忆不延长会话寿命。
        @DefaultValue("24h") Duration retention,
        // 窗口最多保留的完整轮次数，裁剪不能截断单轮工具调用过程。
        @DefaultValue("20") int maxTurns,
        // 单个完整轮次的 UTF-8 序列化字节上限，不是文本字符数。
        @DefaultValue("16384") int maxTurnBytes,
        // 整个快照的 UTF-8 序列化字节上限，包含轮次、版本和提交元数据。
        @DefaultValue("131072") int maxSnapshotBytes,
        // 单轮消息总数上限，工具调用过程中的助手消息和工具结果也计入。
        @DefaultValue("32") int maxMessagesPerTurn) {

    public MemoryProperties {
        if (retention == null || retention.toMillis() <= 0) {
            throw new IllegalArgumentException("记忆保留时长不能小于一毫秒");
        }
        // 快照除轮次内容外还包含版本和提交元数据，容量上限必须大于单轮上限。
        if (maxTurns < 1 || maxTurnBytes < 1 || maxSnapshotBytes <= maxTurnBytes
                || maxMessagesPerTurn < 2) {
            throw new IllegalArgumentException("记忆窗口限制配置无效");
        }
    }
}
