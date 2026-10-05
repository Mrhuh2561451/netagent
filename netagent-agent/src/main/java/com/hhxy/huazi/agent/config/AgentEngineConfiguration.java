package com.hhxy.huazi.agent.config;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * 根据配置创建模型客户端；此处不发起模型请求，也不创建 Agent。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(
        prefix = "netAgent.agent",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AgentEngineConfiguration {

    @Bean
    public OpenAIChatModel agentChatModel(AgentProperties agentProperties) {
        AgentProperties.Chat chat = agentProperties.getChat();

        Assert.notNull(chat, "netAgent.agent.chat 未配置");
        Assert.hasText(chat.getBaseUrl(), "netAgent.agent.chat.base-url 未配置");
        Assert.hasText(chat.getEndpointPath(), "netAgent.agent.chat.endpoint-path 未配置");
        Assert.hasText(chat.getApiKey(),
                "netAgent.agent.chat.api-key 未配置，请设置 LLM_API_KEY 环境变量");
        Assert.hasText(chat.getModel(), "netAgent.agent.chat.model 未配置");

        return OpenAIChatModel.builder()
                .baseUrl(chat.getBaseUrl())
                .endpointPath(chat.getEndpointPath())
                .apiKey(chat.getApiKey())
                .modelName(chat.getModel())
                .stream(true)
                // 兼容无法同时处理原生结构化输出与工具调用的端点。
                .nativeStructuredOutputWithTools(false)
                .build();
    }
}
