package com.hhxy.huazi.memory.model;

// 已鉴权身份显式传入，不能把标识校验当鉴权。
public record MemoryScope(String tenantId, String userId, String agentId, String conversationId) {
    public MemoryScope {
        requireId(tenantId);
        requireId(userId);
        requireId(agentId);
        requireId(conversationId);
    }

    public static String requireId(String value) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new MemoryValidationException("记忆标识必须包含 1 至 256 个字符");
        }
        return value;
    }
}
