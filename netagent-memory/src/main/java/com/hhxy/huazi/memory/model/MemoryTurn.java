package com.hhxy.huazi.memory.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record MemoryTurn(String requestId, List<MemoryMessage> messages, Instant createdAt) {
    public MemoryTurn {
        MemoryScope.requireId(requestId);
        if (createdAt == null || messages == null || messages.size() < 2 || messages.stream().anyMatch(java.util.Objects::isNull)) {
            throw new MemoryValidationException("完整轮次必须包含消息和时间戳");
        }
        messages = List.copyOf(messages);
        if (messages.get(0).role() != MemoryMessage.Role.USER
                || messages.get(messages.size() - 1).role() != MemoryMessage.Role.ASSISTANT
                || !messages.get(messages.size() - 1).toolCalls().isEmpty()) {
            throw new MemoryValidationException("轮次必须以用户输入开始，并以最终回答结束");
        }
        Set<String> messageIds = new HashSet<>();
        Set<String> callIds = new HashSet<>();
        // 同批工具调用必须全部收到结果后才可继续助手输出。
        Set<String> pending = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            MemoryMessage message = messages.get(i);
            if (!messageIds.add(message.messageId()) || (i > 0 && message.role() == MemoryMessage.Role.USER)) {
                throw new MemoryValidationException("消息重复或用户输入位置不正确");
            }
            if (message.role() == MemoryMessage.Role.TOOL) {
                if (!pending.remove(message.toolCallId())) {
                    throw new MemoryValidationException("工具结果必须匹配此前尚未收到结果的工具调用");
                }
            } else {
                if (!pending.isEmpty()) {
                    throw new MemoryValidationException("所有工具结果必须在下一条助手消息之前返回");
                }
                if (i > 0 && i < messages.size() - 1 && message.toolCalls().isEmpty()) {
                    throw new MemoryValidationException("只有最后一条助手消息可以结束轮次");
                }
                for (ToolCall call : message.toolCalls()) {
                    if (!callIds.add(call.id())) {
                        throw new MemoryValidationException("工具调用标识必须唯一");
                    }
                    pending.add(call.id());
                }
            }
        }
        if (!pending.isEmpty()) {
            throw new MemoryValidationException("轮次包含未完成的工具调用");
        }
    }
}
