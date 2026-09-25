package com.hhxy.huazi.memory.cache;

/** 缓存基础设施或事务后失效失败；异常不携带 Redis 敏感细节。 */
public final class CacheUnavailableException extends RuntimeException {
    public CacheUnavailableException() {
        this("缓存基础设施不可用");
    }

    CacheUnavailableException(String message) {
        super(message);
    }
}
