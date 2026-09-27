package com.hhxy.huazi.memory.cache;

import java.util.Objects;

/** 区分回源结果与缓存命中；空值标记不能混同于未命中。 */
public record CacheResult<T>(Status status, T value) {
    public enum Status { MISS, HIT, NULL_HIT, LOADED, LOADED_NULL, UNAVAILABLE }

    public CacheResult {
        Objects.requireNonNull(status, "缓存结果状态不能为空");
        boolean needsValue = status == Status.HIT || status == Status.LOADED;
        if (needsValue != (value != null)) {
            throw new IllegalArgumentException("值与缓存结果状态不匹配");
        }
    }

    /** 只表示 Redis 中已有可用缓存，包含空值命中，但不包含本次回源的 LOADED 或 LOADED_NULL。 */
    public boolean isHit() {
        return status == Status.HIT || status == Status.NULL_HIT;
    }

    /** 未取得可用缓存，不代表权威数据源中不存在该数据。 */
    public static <T> CacheResult<T> miss() {
        return new CacheResult<>(Status.MISS, null);
    }

    /** 基础设施故障导致无法判断命中情况，与正常 MISS 分开报告。 */
    public static <T> CacheResult<T> unavailable() {
        return new CacheResult<>(Status.UNAVAILABLE, null);
    }

    /** null 表示缓存中已存在有效空值标记，而不是缓存键缺失。 */
    public static <T> CacheResult<T> hit(T value) {
        return new CacheResult<>(value == null ? Status.NULL_HIT : Status.HIT, value);
    }

    /** 表示本次数据源调用已成功完成，包括空结果；不承诺该结果已缓存。 */
    public static <T> CacheResult<T> loaded(T value) {
        return new CacheResult<>(value == null ? Status.LOADED_NULL : Status.LOADED, value);
    }

    /** 安全打印仅暴露状态，不输出缓存值或回源数据。 */
    @Override
    public String toString() {
        return "CacheResult[status=" + status + "]";
    }
}
