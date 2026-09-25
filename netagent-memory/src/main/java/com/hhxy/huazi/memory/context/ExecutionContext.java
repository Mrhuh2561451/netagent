package com.hhxy.huazi.memory.context;

/** 身份须由鉴权后的入口提供；上下文透传不替代访问权限校验。 */
public record ExecutionContext(String tenantId, String userId, String traceId) {
    public ExecutionContext {
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()
                || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("执行上下文的租户标识、用户标识和链路标识不能为空");
        }
    }
}
