package com.hhxy.huazi.memory.store;

/** 不透传底层异常正文，避免暴露连接信息或记忆内容。 */
public final class MemoryUnavailableException extends IllegalStateException {
    public MemoryUnavailableException() {
        super("记忆存储不可用");
    }
}
