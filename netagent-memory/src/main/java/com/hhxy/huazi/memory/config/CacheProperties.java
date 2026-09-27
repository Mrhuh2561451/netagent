package com.hhxy.huazi.memory.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 通用缓存的区域白名单及资源限制；缓存只加速权威数据访问，不替代记忆存储。 */
@ConfigurationProperties("netagent.cache")
public record CacheProperties(
        // 普通值的默认 TTL，可被区域 TTL 或调用时显式 TTL 覆盖。
        @DefaultValue("5m") Duration defaultTtl,
        // 空值使用独立的短 TTL，不沿用普通值的区域或显式 TTL。
        @DefaultValue("30s") Duration nullTtl,
        // 过期时间抖动比例必须在 [0,1) 内，用于分散集中失效，不是时长。
        @DefaultValue("0.1") double ttlJitterRatio,
        // 缓存值的序列化字节上限包含信封元数据，而非仅业务载荷。
        @DefaultValue("65536") int maxValueBytes,
        // 等待缓存互斥锁的最长时间，不是权威数据源加载超时。
        @DefaultValue("100ms") Duration lockWait,
        // 仅允许访问预先配置的区域白名单，不能根据用户输入动态新增区域。
        Map<String, Region> regions) {

    public CacheProperties {
        requirePositive(defaultTtl);
        requirePositive(nullTtl);
        if (!Double.isFinite(ttlJitterRatio) || ttlJitterRatio < 0 || ttlJitterRatio >= 1
                || maxValueBytes < 1 || lockWait == null || lockWait.isNegative()) {
            throw new IllegalArgumentException("缓存限制配置无效");
        }
        // 未配置区域时禁用缓存访问，避免隐式共享同一缓存空间。
        regions = regions == null ? Map.of() : Map.copyOf(regions);
        regions.keySet().forEach(name -> {
            if (!name.matches("[A-Za-z0-9._-]{1,64}")) {
                throw new IllegalArgumentException("缓存区域名称无效");
            }
        });
    }

    private static void requirePositive(Duration ttl) {
        if (ttl == null || ttl.toMillis() <= 0) {
            throw new IllegalArgumentException("缓存有效期不能小于一毫秒");
        }
    }

    /** 允许降级只影响缓存基础设施故障，不能吞掉权威数据源的业务异常。 */
    // ttl 为 null 时继承 defaultTtl；cacheNulls 控制是否缓存空值，failOpen 控制缓存基础设施故障时是否允许绕过缓存。
    public record Region(Duration ttl, boolean cacheNulls, boolean failOpen) {
        public Region {
            if (ttl != null) {
                requirePositive(ttl);
            }
        }
    }
}
