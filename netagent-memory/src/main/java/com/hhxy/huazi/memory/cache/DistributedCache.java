package com.hhxy.huazi.memory.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;

/**
 * 为可信、显式类型的 DTO 提供旁路缓存，不替代权威数据源。
 * 读取不续期；显式写入 TTL 只影响非空值，空值始终使用配置的短 TTL。
 * 禁止空值或载荷超限时删除已有缓存；删除失败不能报告成功跳过。
 * 允许降级的区域通过 UNAVAILABLE 报告单次访问失败，其余区域抛出不可用异常。
 */
public interface DistributedCache {
    enum WriteResult { STORED, SKIPPED_NULL, SKIPPED_TOO_LARGE, UNAVAILABLE }
    enum EvictionResult { EVICTED, ABSENT, UNAVAILABLE }

    @FunctionalInterface
    interface Loader<T> {
        /** 同步调用；实际的数据源超时必须由调用方配置。 */
        T load() throws Exception;
    }

    <T> CacheResult<T> get(CacheKey key, Class<T> type);
    <T> CacheResult<T> get(CacheKey key, TypeReference<T> type);

    <T> WriteResult put(CacheKey key, T value, Class<T> type);
    <T> WriteResult put(CacheKey key, T value, TypeReference<T> type);
    <T> WriteResult put(CacheKey key, T value, Class<T> type, Duration ttl);
    <T> WriteResult put(CacheKey key, T value, TypeReference<T> type, Duration ttl);

    EvictionResult evict(CacheKey key);

    /** 繁忙或禁止降级区域的基础设施故障抛出异常，不能伪装为空值命中。 */
    <T> CacheResult<T> getOrLoad(CacheKey key, Class<T> type, Loader<T> loader) throws Exception;
    <T> CacheResult<T> getOrLoad(CacheKey key, TypeReference<T> type, Loader<T> loader) throws Exception;
}
