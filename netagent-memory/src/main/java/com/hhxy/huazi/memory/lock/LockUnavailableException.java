package com.hhxy.huazi.memory.lock;

/** 获取锁时的基础设施故障，区别于普通竞争失败和业务回调异常。 */
public final class LockUnavailableException extends RuntimeException {
    public LockUnavailableException() {
        // 不透传基础设施异常正文，避免泄漏锁名或敏感信息。
        super("分布式锁基础设施不可用");
    }
}
