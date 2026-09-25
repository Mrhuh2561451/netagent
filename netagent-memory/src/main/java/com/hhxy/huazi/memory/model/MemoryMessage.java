package com.hhxy.huazi.memory.model;

import java.util.List;

public record MemoryMessage(String messageId, Role role, String text, String toolCallId,
                            List<ToolCall> toolCalls) {
    public enum Role { USER, ASSISTANT, TOOL }

    public MemoryMessage {
        MemoryScope.requireId(messageId);
        if (role == null || text == null || toolCalls == null || toolCalls.stream().anyMatch(java.util.Objects::isNull)) {
            throw new MemoryValidationException("消息字段不能为空");
        }
        toolCalls = List.copyOf(toolCalls);
        if (role == Role.TOOL) {
            MemoryScope.requireId(toolCallId);
            if (!toolCalls.isEmpty()) {
                throw new MemoryValidationException("工具结果不能发起工具调用");
            }
        } else if (toolCallId != null) {
            throw new MemoryValidationException("只有工具结果可以引用工具调用");
        }
        if (role != Role.ASSISTANT && !toolCalls.isEmpty()) {
            throw new MemoryValidationException("只有助手消息可以发起工具调用");
        }
    }
}
