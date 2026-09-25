package com.hhxy.huazi.memory.cache;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hhxy.huazi.memory.config.CacheProperties;
import com.hhxy.huazi.memory.lock.DistributedLockExecutor;
import com.hhxy.huazi.memory.lock.LockUnavailableException;
import com.hhxy.huazi.memory.support.RedisKeyFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.PlainOptions;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用带类型约束的 JSON 缓存，只反序列化调用方明确指定的可信 DTO 类型。
 * 区域名只能来自服务端配置，不提供全局兜底区域。
 */
public final class RedissonDistributedCache implements DistributedCache {
    private static final Logger LOG = LoggerFactory.getLogger(RedissonDistributedCache.class);
    private static final int SCHEMA_VERSION = 1;
    // 即使可信 DTO 带有基于类名的多态注解，也在解析缓存提供的类名之前拒绝。
    private static final PolymorphicTypeValidator NO_CLASS_POLYMORPHISM = new PolymorphicTypeValidator.Base() {
        @Override
        public Validity validateBaseType(MapperConfig<?> config, JavaType baseType) {
            return Validity.DENIED;
        }
    };
    // 每实例、每固定区域的所有回源共享本地预算，不承诺集群并发上限。
    static final int LOAD_CONCURRENCY = 4;

    private final RedissonClient client;
    private final RedisKeyFactory keys;
    private final ObjectMapper mapper;
    private final CacheProperties properties;
    private final DistributedLockExecutor locks;
    private final MeterRegistry meters;
    private final Map<String, Semaphore> loadBudgets;

    public RedissonDistributedCache(RedissonClient client, RedisKeyFactory keys, ObjectMapper mapper,
                                    CacheProperties properties, DistributedLockExecutor locks,
                                    MeterRegistry meters) {
        this.client = Objects.requireNonNull(client, "Redis 客户端不能为空");
        this.keys = Objects.requireNonNull(keys, "Redis 键工厂不能为空");
        this.properties = Objects.requireNonNull(properties, "缓存配置不能为空");
        this.locks = Objects.requireNonNull(locks, "分布式锁执行器不能为空");
        this.meters = Objects.requireNonNull(meters, "指标注册表不能为空");
        // 只使用 mapper 副本，不修改共享 mapper 或客户端的全局 Codec。
        this.mapper = Objects.requireNonNull(mapper, "JSON 映射器不能为空").copy().deactivateDefaultTyping()
                .setPolymorphicTypeValidator(NO_CLASS_POLYMORPHISM)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        Map<String, Semaphore> budgets = new HashMap<>();
        properties.regions().keySet().forEach(name -> budgets.put(name, new Semaphore(LOAD_CONCURRENCY)));
        this.loadBudgets = Map.copyOf(budgets);
    }

    @Override
    public <T> CacheResult<T> get(CacheKey key, Class<T> type) {
        CacheProperties.Region region = region(key);
        return read(key, javaType(type), region);
    }

    @Override
    public <T> CacheResult<T> get(CacheKey key, TypeReference<T> type) {
        CacheProperties.Region region = region(key);
        return read(key, javaType(type), region);
    }

    @Override
    public <T> WriteResult put(CacheKey key, T value, Class<T> type) {
        CacheProperties.Region region = region(key);
        return write(key, value, javaType(type), region, null);
    }

    @Override
    public <T> WriteResult put(CacheKey key, T value, TypeReference<T> type) {
        CacheProperties.Region region = region(key);
        return write(key, value, javaType(type), region, null);
    }

    @Override
    public <T> WriteResult put(CacheKey key, T value, Class<T> type, Duration ttl) {
        CacheProperties.Region region = region(key);
        positiveMillis(ttl);
        return write(key, value, javaType(type), region, ttl);
    }

    @Override
    public <T> WriteResult put(CacheKey key, T value, TypeReference<T> type, Duration ttl) {
        CacheProperties.Region region = region(key);
        positiveMillis(ttl);
        return write(key, value, javaType(type), region, ttl);
    }

    @Override
    public EvictionResult evict(CacheKey key) {
        return delete(key, region(key));
    }

    @Override
    public <T> CacheResult<T> getOrLoad(CacheKey key, Class<T> type, Loader<T> loader) throws Exception {
        CacheProperties.Region region = region(key);
        return load(key, javaType(type), region, Objects.requireNonNull(loader, "回源加载器不能为空"));
    }

    @Override
    public <T> CacheResult<T> getOrLoad(CacheKey key, TypeReference<T> type, Loader<T> loader) throws Exception {
        CacheProperties.Region region = region(key);
        return load(key, javaType(type), region, Objects.requireNonNull(loader, "回源加载器不能为空"));
    }

    private <T> CacheResult<T> load(CacheKey key, JavaType type, CacheProperties.Region region,
                                  Loader<T> loader) throws Exception {
        CacheResult<T> first = read(key, type, region);
        if (first.isHit()) {
            return first;
        }
        if (first.status() == CacheResult.Status.UNAVAILABLE) {
            return degradedLoad(key, loader);
        }

        String lockName = keys.cacheLockKey(key.region(), key.tenantId(), key.userId(), key.businessId());
        AtomicBoolean callbackEntered = new AtomicBoolean();
        DistributedLockExecutor.Execution<CacheResult<T>> execution;
        try {
            execution = locks.tryExecute(lockName, properties.lockWait(), () -> {
                callbackEntered.set(true);
                // 等待锁时其他实例可能已回填缓存，二次检查避免重复回源。
                CacheResult<T> second = read(key, type, region);
                if (second.isHit()) {
                    return second;
                }
                if (second.status() == CacheResult.Status.UNAVAILABLE) {
                    return degradedLoad(key, loader);
                }
                T value = invokeLoader(key.region(), "normal", loader);
                // 只处理已成功回源后的写缓存故障；回源异常不进入此分支，也不会重试回源。
                try {
                    write(key, value, type, region, null);
                } catch (RuntimeException e) {
                    if (!region.failOpen()) {
                        throw e;
                    }
                    counter("netagent.cache.errors", key.region(), "populate");
                    LOG.warn("缓存回填失败（{}）", e.getClass().getSimpleName());
                }
                return CacheResult.loaded(value);
            });
        } catch (LockUnavailableException e) {
            // 业务回源自身也可能抛出此类型；进入回调后必须原样传播，不能再次回源。
            if (callbackEntered.get()) {
                throw e;
            }
            counter("netagent.cache.errors", key.region(), "lock_unavailable");
            if (!region.failOpen()) {
                throw new CacheUnavailableException();
            }
            return degradedLoad(key, loader);
        }
        if (execution.acquired()) {
            if (execution.releaseStatus() != DistributedLockExecutor.ReleaseStatus.RELEASED) {
                counter("netagent.cache.errors", key.region(), "lock_release");
                if (!region.failOpen()) {
                    throw new CacheUnavailableException();
                }
            }
            return execution.value();
        }

        // tryExecute 已完成有界等待；这里只重读一次，不再次循环抢锁。
        CacheResult<T> last = read(key, type, region);
        if (last.isHit()) {
            return last;
        }
        if (last.status() == CacheResult.Status.UNAVAILABLE) {
            return degradedLoad(key, loader);
        }
        counter("netagent.cache.requests", key.region(), "busy");
        throw new CacheBusyException();
    }

    private <T> CacheResult<T> degradedLoad(CacheKey key, Loader<T> loader) throws Exception {
        return CacheResult.loaded(invokeLoader(key.region(), "degraded", loader));
    }

    private <T> T invokeLoader(String region, String mode, Loader<T> loader) throws Exception {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("缓存回源前线程已被中断");
        }
        Semaphore budget = loadBudgets.get(region);
        if (!budget.tryAcquire()) {
            counter("netagent.cache.requests", region, "busy");
            throw new CacheBusyException();
        }
        long started = System.nanoTime();
        String outcome = "error";
        try {
            T value = loader.load();
            outcome = value == null ? "null" : "success";
            return value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = "interrupted";
            throw e;
        } finally {
            budget.release();
            try {
                meters.timer("netagent.cache.load.duration", "region", region, "mode", mode, "outcome", outcome)
                        .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            } catch (RuntimeException ignored) {
                // 观测失败不覆盖回源结果或原始异常。
            }
        }
    }

    private <T> CacheResult<T> read(CacheKey key, JavaType type, CacheProperties.Region region) {
        String json;
        try {
            json = bucket(key).get();
        } catch (RuntimeException e) {
            unavailable(key.region(), region, "read", e);
            return CacheResult.unavailable();
        }
        if (json == null) {
            return miss(key.region(), null);
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > properties.maxValueBytes()) {
            return miss(key.region(), "oversized_payload");
        }
        try {
            JsonNode envelope = mapper.readTree(json);
            if (envelope == null || !envelope.isObject()) {
                return miss(key.region(), "invalid_payload");
            }
            JsonNode version = envelope.path("schemaVersion");
            if (!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() != SCHEMA_VERSION) {
                return miss(key.region(), "unknown_schema");
            }
            if (!typeDigest(type).equals(envelope.path("typeDigest").textValue())) {
                return miss(key.region(), "type_mismatch");
            }
            JsonNode nullValue = envelope.path("nullValue");
            JsonNode payload = envelope.get("value");
            if (!nullValue.isBoolean() || payload == null || nullValue.booleanValue() != payload.isNull()) {
                return miss(key.region(), "invalid_payload");
            }
            if (nullValue.booleanValue()) {
                if (!region.cacheNulls()) {
                    return miss(key.region(), "null_disabled");
                }
                counter("netagent.cache.requests", key.region(), "null_hit");
                return CacheResult.hit(null);
            }
            T value = mapper.readerFor(type).readValue(payload);
            if (value == null) {
                return miss(key.region(), "invalid_payload");
            }
            counter("netagent.cache.requests", key.region(), "hit");
            return CacheResult.hit(value);
        } catch (IOException | RuntimeException e) {
            LOG.warn("缓存载荷解码失败（{}）", e.getClass().getSimpleName());
            return miss(key.region(), "invalid_payload");
        }
        // 坏数据或未知版本可能来自较新的写入方，读取路径绝不自动删除。
    }

    private <T> WriteResult write(CacheKey key, T value, JavaType type, CacheProperties.Region region,
                                  Duration overrideTtl) {
        if (value == null && !region.cacheNulls()) {
            return skipAndInvalidate(key, region, WriteResult.SKIPPED_NULL, "null_disabled");
        }
        Duration ttl = value == null ? properties.nullTtl()
                : overrideTtl != null ? overrideTtl : region.ttl() != null ? region.ttl() : properties.defaultTtl();
        Duration actualTtl = jitteredTtl(ttl);
        final String json;
        try {
            ObjectNode envelope = mapper.createObjectNode();
            envelope.put("schemaVersion", SCHEMA_VERSION);
            // 只比较调用方声明的类型摘要，绝不根据缓存中的类名解析 Java 类型。
            envelope.put("typeDigest", typeDigest(type));
            envelope.put("nullValue", value == null);
            JsonNode payload = value == null ? mapper.nullNode()
                    : mapper.readTree(mapper.writerFor(type).writeValueAsBytes(value));
            // 使用读取路径的受控类型校验，拒绝写入后永远无法命中的载荷（含嵌套多态类型）。
            if (value != null && mapper.readerFor(type).readValue(payload) == null) {
                throw new IllegalArgumentException("缓存 DTO 序列化后再反序列化得到空值");
            }
            envelope.set("value", payload);
            json = mapper.writeValueAsString(envelope);
        } catch (IOException | RuntimeException e) {
            counter("netagent.cache.errors", key.region(), "encode");
            LOG.warn("缓存载荷编码失败（{}）", e.getClass().getSimpleName());
            throw new IllegalArgumentException("值无法按声明的缓存 DTO 类型编码");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > properties.maxValueBytes()) {
            return skipAndInvalidate(key, region, WriteResult.SKIPPED_TOO_LARGE, "oversized_payload");
        }
        try {
            bucket(key).set(json, actualTtl);
            counter("netagent.cache.writes", key.region(), "stored");
            return WriteResult.STORED;
        } catch (RuntimeException e) {
            unavailable(key.region(), region, "write", e);
            return WriteResult.UNAVAILABLE;
        }
    }

    private WriteResult skipAndInvalidate(CacheKey key, CacheProperties.Region region,
                                          WriteResult result, String outcome) {
        counter("netagent.cache.writes", key.region(), outcome);
        // 新结果不适合缓存时必须移除旧值，避免旧值仍被误认为有效的替换结果。
        // Redis 删除失败明确返回不可用或抛出异常，不能声称已成功失效。
        return delete(key, region) == EvictionResult.UNAVAILABLE ? WriteResult.UNAVAILABLE : result;
    }

    private EvictionResult delete(CacheKey key, CacheProperties.Region region) {
        try {
            return bucket(key).delete() ? EvictionResult.EVICTED : EvictionResult.ABSENT;
        } catch (RuntimeException e) {
            unavailable(key.region(), region, "evict", e);
            return EvictionResult.UNAVAILABLE;
        }
    }

    private RBucket<String> bucket(CacheKey key) {
        // 禁止隐式重试，以免一次调用超出既定等待预算。
        return client.getBucket(PlainOptions.name(
                keys.cacheKey(key.region(), key.tenantId(), key.userId(), key.businessId()))
                .codec(StringCodec.INSTANCE).retryAttempts(0));
    }

    private CacheProperties.Region region(CacheKey key) {
        Objects.requireNonNull(key, "缓存键不能为空");
        CacheProperties.Region region = properties.regions().get(key.region());
        if (region == null) {
            throw new IllegalArgumentException("未知的缓存区域");
        }
        return region;
    }

    private JavaType javaType(Class<?> type) {
        return mapper.constructType(Objects.requireNonNull(type, "缓存值类型不能为空"));
    }

    private JavaType javaType(TypeReference<?> type) {
        return mapper.constructType(Objects.requireNonNull(type, "缓存值类型不能为空").getType());
    }

    private String typeDigest(JavaType type) {
        return RedisKeyFactory.digest(type.toCanonical());
    }

    private <T> CacheResult<T> miss(String region, String reason) {
        counter("netagent.cache.requests", region, "miss");
        if (reason != null) {
            counter("netagent.cache.errors", region, reason);
        }
        return CacheResult.miss();
    }

    private void unavailable(String regionName, CacheProperties.Region region, String operation,
                             RuntimeException failure) {
        counter("netagent.cache.errors", regionName, operation);
        counter("netagent.cache.requests", regionName, "unavailable");
        LOG.warn("缓存 Redis 操作失败（{}）", failure.getClass().getSimpleName());
        if (!region.failOpen()) {
            throw new CacheUnavailableException();
        }
    }

    private void counter(String name, String region, String outcome) {
        try {
            meters.counter(name, "region", region, "outcome", outcome).increment();
        } catch (RuntimeException ignored) {
            // 区域已经过配置校验；key、类型名、异常正文均不进入指标标签。
        }
    }

    Duration jitteredTtl(Duration ttl) {
        long millis = positiveMillis(ttl);
        double ratio = properties.ttlJitterRatio();
        // 通过随机抖动分散缓存集中到期的时间。
        double multiplier = ratio == 0 ? 1 : 1 + ThreadLocalRandom.current().nextDouble(-ratio, ratio);
        return Duration.ofMillis(Math.max(1, Math.round(millis * multiplier)));
    }

    private static long positiveMillis(Duration ttl) {
        Objects.requireNonNull(ttl, "缓存有效期不能为空");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("缓存有效期必须为正数");
        }
        try {
            return Math.max(1, ttl.toMillis());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("缓存有效期过大");
        }
    }
}
