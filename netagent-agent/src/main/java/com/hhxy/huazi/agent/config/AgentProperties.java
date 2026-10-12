package com.hhxy.huazi.agent.config;

import jakarta.validation.constraints.NotNull;
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
    /**
     * SSE 通道超时
     */
    @NotNull
    private Long sseTimeoutMs = 900_000L;

    /**
     * ReAct 循环上限，超出后由框架熔断收尾
     */
    private Integer maxIters = 10;
    /**
     * 单次模型调用的最大尝试次数（含首次），1 即不重试
     * 重试加在整条流之上，半程失败会重订阅，已吐出的正文不回滚
     */
    private Integer maxRetries = 1;

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
