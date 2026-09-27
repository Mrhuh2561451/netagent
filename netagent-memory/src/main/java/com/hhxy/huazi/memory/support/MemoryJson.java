package com.hhxy.huazi.memory.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** 记忆格式和摘要共用确定性序列化规则，不能随业务接口的 JSON 展示配置改变。 */
public final class MemoryJson {
    private MemoryJson() {}

    /** 每次返回独立实例，调用方可增加严格校验而不改变其他组件的解析行为。 */
    public static ObjectMapper createMapper() {
        // 固定属性与映射键的顺序，避免同一内容因序列化顺序不同而产生不同提交摘要。
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                // 时间使用文本格式，避免数值时间戳与 Lua 的时间格式校验不一致。
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
