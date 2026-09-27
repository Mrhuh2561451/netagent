package com.hhxy.huazi.memory.model;

/** 表示模型字段或领域约束不合法，区别于存储格式损坏、基础设施不可用；原样重试不能修复这类合法性错误。 */
public class MemoryValidationException extends IllegalArgumentException {
    public MemoryValidationException(String message) {
        super(message);
    }
}
