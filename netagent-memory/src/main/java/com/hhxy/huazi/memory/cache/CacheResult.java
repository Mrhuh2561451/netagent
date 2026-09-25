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

    public boolean isHit() {
        return status == Status.HIT || status == Status.NULL_HIT;
    }

    public static <T> CacheResult<T> miss() {
        return new CacheResult<>(Status.MISS, null);
    }

    public static <T> CacheResult<T> unavailable() {
        return new CacheResult<>(Status.UNAVAILABLE, null);
    }

    public static <T> CacheResult<T> hit(T value) {
        return new CacheResult<>(value == null ? Status.NULL_HIT : Status.HIT, value);
    }

    public static <T> CacheResult<T> loaded(T value) {
        return new CacheResult<>(value == null ? Status.LOADED_NULL : Status.LOADED, value);
    }

    @Override
    public String toString() {
        return "CacheResult[status=" + status + "]";
    }
}
