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
    // STORED 表示已写入；两种 SKIPPED 表示因禁缓存空值或载荷超限跳过且旧值已失效；UNAVAILABLE 表示操作失败，不能确认写入或失效。
    enum WriteResult { STORED, SKIPPED_NULL, SKIPPED_TOO_LARGE, UNAVAILABLE }
    // EVICTED 表示已删除；ABSENT 表示原本不存在，同样满足失效要求；UNAVAILABLE 表示无法确认失效。
    enum EvictionResult { EVICTED, ABSENT, UNAVAILABLE }

    /** null 表示数据源无结果；失败应抛出异常，不能用 null 掩盖回源故障。 */
    @FunctionalInterface
    interface Loader<T> {
        /** 同步调用；实际的数据源超时必须由调用方配置。 */
        T load() throws Exception;
    }

    /** 按调用方提供的可信具体 DTO 类型读取，不续期；NULL_HIT 表示已缓存空值，不等同于 MISS。 */
    <T> CacheResult<T> get(CacheKey key, Class<T> type);
    /** 用 TypeReference 保留可信 DTO 的泛型信息，读取不续期；NULL_HIT 与 MISS 必须分别处理。 */
    <T> CacheResult<T> get(CacheKey key, TypeReference<T> type);

    /** 按可信具体 DTO 类型写入，非空值采用区域或默认 TTL，空值采用短 TTL；禁缓存空值或超限时须使旧值失效。 */
    <T> WriteResult put(CacheKey key, T value, Class<T> type);
    /** 用 TypeReference 保留可信 DTO 的泛型信息，非空值采用区域或默认 TTL，空值采用短 TTL；禁缓存空值或超限时须使旧值失效。 */
    <T> WriteResult put(CacheKey key, T value, TypeReference<T> type);
    /** 按可信具体 DTO 类型写入；显式 TTL 必须为正且不覆盖空值短 TTL，禁缓存空值或超限时须使旧值失效。 */
    <T> WriteResult put(CacheKey key, T value, Class<T> type, Duration ttl);
    /** 用 TypeReference 保留可信 DTO 的泛型信息；显式 TTL 必须为正且不覆盖空值短 TTL，禁缓存空值或超限时须使旧值失效。 */
    <T> WriteResult put(CacheKey key, T value, TypeReference<T> type, Duration ttl);

    /** 删除指定键，原本不存在也视为满足失效要求；故障按区域策略返回 UNAVAILABLE 或抛出异常。 */
    EvictionResult evict(CacheKey key);

    // 按可信具体 DTO 类型读取，命中空值也不回源且读取不续期；回源异常原样传播，线程中断保留。
    /** 繁忙或禁止降级区域的基础设施故障抛出异常，不能伪装为空值命中。 */
    <T> CacheResult<T> getOrLoad(CacheKey key, Class<T> type, Loader<T> loader) throws Exception;
    /** 用 TypeReference 保留可信 DTO 的泛型信息，命中空值也不回源且读取不续期；回源异常原样传播并保留中断，竞争繁忙抛出异常而非空命中。 */
    <T> CacheResult<T> getOrLoad(CacheKey key, TypeReference<T> type, Loader<T> loader) throws Exception;
}
