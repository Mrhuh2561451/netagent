package com.hhxy.huazi.memory.model;

/** 租户、用户、智能体、会话四维共同界定记忆隔离范围，不能仅凭会话标识跨范围访问。 */
// 已鉴权身份显式传入，不能把标识校验当鉴权。
public record MemoryScope(String tenantId, String userId, String agentId, String conversationId) {
    public MemoryScope {
        requireId(tenantId);
        requireId(userId);
        requireId(agentId);
        requireId(conversationId);
    }

    // 按 Java 字符串长度限制为 1 至 256 个 UTF-16 代码单元并拒绝纯空白，不是 UTF-8 字节上限，也不裁剪或规范化标识。
    public static String requireId(String value) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new MemoryValidationException("记忆标识必须包含 1 至 256 个字符");
        }
        return value;
    }
}
