package com.hhxy.huazi.memory.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhxy.huazi.memory.support.MemoryJson;

import java.util.Map;

public record ToolCall(String id, String name, String arguments) {
    private static final ObjectMapper JSON = MemoryJson.createMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

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
            throw new MemoryValidationException("工具参数必须是有效的 JSON 对象");
        }
    }
}
