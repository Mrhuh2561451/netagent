package com.hhxy.huazi.memory.model;

import java.util.List;

/** USER、ASSISTANT、TOOL 分别承载用户输入、助手输出和工具结果；结构化调用记录放在 toolCalls 中，不以文本代替。 */
public record MemoryMessage(String messageId, Role role, String text, String toolCallId,
                            List<ToolCall> toolCalls) {
    // 角色决定消息在轮次中的位置及可携带的调用字段，TOOL 表示调用结果而非调用请求。
    public enum Role { USER, ASSISTANT, TOOL }

    // toolCallId 仅供 TOOL 引用已有调用，toolCalls 中的调用记录仅由 ASSISTANT 发起；跨消息配对由轮次校验。
    public MemoryMessage {
        MemoryScope.requireId(messageId);
        if (role == null || text == null || toolCalls == null || toolCalls.stream().anyMatch(java.util.Objects::isNull)) {
            throw new MemoryValidationException("消息字段不能为空");
        }
        // 固化调用列表，防止外部后续修改破坏已经验证的轮次结构。
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
