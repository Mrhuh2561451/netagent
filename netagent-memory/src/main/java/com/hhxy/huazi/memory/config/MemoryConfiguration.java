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

/** 分别装配记忆持久化、窗口策略与通用缓存，共享 Redis 基础设施不等于共享一致性或失效语义。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MemoryProperties.class, CacheProperties.class})
@Import(ExecutionContextConfiguration.class)
public class MemoryConfiguration {
    // 将部署环境纳入 Redis 键隔离，避免不同环境在同一 Redis 上碰撞；环境隔离不替代身份鉴权。
    @Bean
    public RedisKeyFactory redisKeyFactory(@Value("${netagent.environment:local}") String environment) {
        return new RedisKeyFactory(environment);
    }

    // 将消息数、完整轮次数及序列化大小限制交给窗口策略统一执行，而非由存储任意截断消息。
    @Bean
    public TurnWindowPolicy turnWindowPolicy(MemoryProperties properties) {
        return new TurnWindowPolicy(properties);
    }

    // 记忆存储承载会话快照及版本化写入，不与可降级的通用缓存互相替代。
    @Bean
    public MemoryStore memoryStore(RedissonClient client, RedisKeyFactory keys, MemoryProperties properties) {
        return new RedisMemoryStore(client, keys, properties);
    }

    // 服务协调窗口策略与存储的版本比较写入，以乐观并发提交轮次，不靠长时间持锁覆盖对话执行。
    @Bean
    public ConversationMemoryService conversationMemoryService(MemoryStore store, TurnWindowPolicy policy,
                                                               MeterRegistry metrics) {
        return new ConversationMemoryService(store, policy, metrics);
    }

    // 分布式锁仅用于短临界区，不应包住模型推理或整个工具执行链，也不能替代存储的版本校验。
    @Bean
    public DistributedLockExecutor distributedLockExecutor(RedissonClient client, MeterRegistry metrics) {
        return new DistributedLockExecutor(client, metrics);
    }

    // 有事务时将缓存失效延后到成功提交之后，避免数据库回滚却提前改变缓存可见性。
    @Bean
    public TransactionalCacheInvalidator transactionalCacheInvalidator(DistributedCache cache) {
        return new TransactionalCacheInvalidator(cache);
    }

    // 区域配置决定空值缓存及基础设施降级边界，不提供未配置区域的默认兜底。
    @Bean
    public DistributedCache distributedCache(RedissonClient client, RedisKeyFactory keys,
                                             CacheProperties properties, DistributedLockExecutor locks,
                                             MeterRegistry metrics) {
        // 独立创建序列化器，避免业务 JSON 配置改变缓存格式或反序列化边界。
        return new RedissonDistributedCache(client, keys, MemoryJson.createMapper(), properties, locks, metrics);
    }
}
