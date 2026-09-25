package com.hhxy.huazi.memory.cache;

/** 显式传入已鉴权的作用域；缓存组件另行校验区域是否已配置。 */
public record CacheKey(String region, String tenantId, String userId, String businessId) {
    public CacheKey {
        requireText(region, "缓存区域");
        requireText(tenantId, "租户标识");
        requireText(userId, "用户标识");
        requireText(businessId, "业务标识");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }

    @Override
    public String toString() {
        return "CacheKey[已脱敏]";
    }
}
