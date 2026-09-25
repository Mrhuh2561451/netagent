package com.hhxy.huazi.memory.cache;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

public final class TransactionalCacheInvalidator {
    private final DistributedCache cache;

    public TransactionalCacheInvalidator(DistributedCache cache) {
        this.cache = Objects.requireNonNull(cache, "缓存组件不能为空");
    }

    /** 仅在实际事务提交后失效缓存；回调失败时数据库已提交，不可自动重放事务。 */
    public void evictAfterCommit(CacheKey key) {
        Objects.requireNonNull(key, "缓存键不能为空");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("缓存失效需要已激活的事务和事务同步机制");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                DistributedCache.EvictionResult result;
                try {
                    result = cache.evict(key);
                } catch (RuntimeException e) {
                    throw new CacheUnavailableException("事务提交后缓存失效失败");
                }
                if (result != DistributedCache.EvictionResult.EVICTED
                        && result != DistributedCache.EvictionResult.ABSENT) {
                    throw new CacheUnavailableException("事务提交后缓存失效失败");
                }
            }
        });
    }
}
