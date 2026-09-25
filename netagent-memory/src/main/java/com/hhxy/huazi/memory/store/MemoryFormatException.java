package com.hhxy.huazi.memory.store;

/** 格式异常不触发自动清理，避免旧版本代码破坏较新版本写入的数据。 */
public final class MemoryFormatException extends IllegalStateException {
    public MemoryFormatException() {
        super("记忆状态格式无效或不受支持，已保留原有存储数据");
    }
}
