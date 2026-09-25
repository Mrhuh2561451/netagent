package com.hhxy.huazi.memory.cache;

/** 其他请求正在重建，或本实例的降级回源并发预算已耗尽。 */
public final class CacheBusyException extends RuntimeException {
    public CacheBusyException() {
        super("缓存重建繁忙");
    }
}
