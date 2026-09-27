package com.hhxy.huazi.memory.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhxy.huazi.memory.support.MemoryJson;

import java.util.Map;

/** 记录助手发起的结构化工具调用；arguments 是受控解析的 JSON 对象数据，不是待执行代码，也不授予工具执行权限。 */
public record ToolCall(String id, String name, String arguments) {
    // 精确解析整数和小数，避免数值精度损失改变摘要；拒绝重复字段和尾随内容，消除参数解释歧义。
    private static final ObjectMapper JSON = MemoryJson.createMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    // 只接受 JSON 对象，并通过受控序列化器稳定排序对象键，使摘要不依赖输入字段顺序。
    public ToolCall {
        MemoryScope.requireId(id);
        MemoryScope.requireId(name);
        if (arguments == null || arguments.isBlank()) {
            throw new MemoryValidationException("工具参数必须是 JSON 对象");
        }
        try {
            Object value = JSON.readValue(arguments, Object.class);
            if (!(value instanceof Map)) {
                throw new MemoryValidationException("工具参数必须是 JSON 对象");
            }
            // 规范化参数 JSON，保持摘要稳定。
            arguments = JSON.writeValueAsString(value);
        } catch (Exception ignored) {
            // 对外统一校验错误，不透传可能包含业务参数的解析异常或原始输入。
            throw new MemoryValidationException("工具参数必须是有效的 JSON 对象");
        }
    }
}
