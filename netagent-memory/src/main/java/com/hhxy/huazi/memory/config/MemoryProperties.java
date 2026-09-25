package com.hhxy.huazi.memory.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("netagent.memory")
public record MemoryProperties(
        @DefaultValue("24h") Duration retention,
        @DefaultValue("20") int maxTurns,
        @DefaultValue("16384") int maxTurnBytes,
        @DefaultValue("131072") int maxSnapshotBytes,
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
