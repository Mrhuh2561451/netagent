package com.hhxy.huazi.memory.config;

import com.hhxy.huazi.memory.cache.DistributedCache;
import com.hhxy.huazi.memory.cache.RedissonDistributedCache;
import com.hhxy.huazi.memory.cache.TransactionalCacheInvalidator;
import com.hhxy.huazi.memory.lock.DistributedLockExecutor;
import com.hhxy.huazi.memory.policy.TurnWindowPolicy;
import com.hhxy.huazi.memory.service.ConversationMemoryService;
import com.hhxy.huazi.memory.store.MemoryStore;
import com.hhxy.huazi.memory.store.RedisMemoryStore;
import com.hhxy.huazi.memory.support.MemoryJson;
import com.hhxy.huazi.memory.support.RedisKeyFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MemoryProperties.class, CacheProperties.class})
@Import(ExecutionContextConfiguration.class)
public class MemoryConfiguration {
    @Bean
    public RedisKeyFactory redisKeyFactory(@Value("${netagent.environment:local}") String environment) {
        return new RedisKeyFactory(environment);
    }

    @Bean
    public TurnWindowPolicy turnWindowPolicy(MemoryProperties properties) {
        return new TurnWindowPolicy(properties);
    }

    @Bean
    public MemoryStore memoryStore(RedissonClient client, RedisKeyFactory keys, MemoryProperties properties) {
        return new RedisMemoryStore(client, keys, properties);
    }

    @Bean
    public ConversationMemoryService conversationMemoryService(MemoryStore store, TurnWindowPolicy policy,
                                                               MeterRegistry metrics) {
        return new ConversationMemoryService(store, policy, metrics);
    }

    @Bean
    public DistributedLockExecutor distributedLockExecutor(RedissonClient client, MeterRegistry metrics) {
        return new DistributedLockExecutor(client, metrics);
    }

    @Bean
    public TransactionalCacheInvalidator transactionalCacheInvalidator(DistributedCache cache) {
        return new TransactionalCacheInvalidator(cache);
    }

    @Bean
    public DistributedCache distributedCache(RedissonClient client, RedisKeyFactory keys,
                                             CacheProperties properties, DistributedLockExecutor locks,
                                             MeterRegistry metrics) {
        // 独立创建序列化器，避免业务 JSON 配置改变缓存格式或反序列化边界。
        return new RedissonDistributedCache(client, keys, MemoryJson.createMapper(), properties, locks, metrics);
    }
}
