package com.hhxy.huazi.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 配置映射，模型连接参数由启动模块提供。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "net-agent.agent")
public class AgentProperties {

    private boolean enabled = true;

    private Chat chat = new Chat();

    @Getter
    @Setter
    public static class Chat {

        // OpenAI API base URL
        private String baseUrl;

        // OpenAI API endpoint path
        private String endpointPath;

        // OpenAI API key
        private String apiKey;

        // OpenAI API model
        private String model;
    }
}
