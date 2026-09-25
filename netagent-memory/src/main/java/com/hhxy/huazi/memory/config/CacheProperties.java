package com.hhxy.huazi.memory.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("netagent.cache")
public record CacheProperties(
        @DefaultValue("5m") Duration defaultTtl,
        @DefaultValue("30s") Duration nullTtl,
        @DefaultValue("0.1") double ttlJitterRatio,
        @DefaultValue("65536") int maxValueBytes,
        @DefaultValue("100ms") Duration lockWait,
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
    public record Region(Duration ttl, boolean cacheNulls, boolean failOpen) {
        public Region {
            if (ttl != null) {
                requirePositive(ttl);
            }
        }
    }
}
