package com.hhxy.huazi.agent.config;

import com.hhxy.huazi.agent.state.PgAgentStateStore;
import io.agentscope.core.ReActAgent;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReActAgentProvider {

    private  final OpenAIChatModel openAIChatModel;

    private final  AgentProperties agentProperties;

    private final PgAgentStateStore agentStateStore;

    public ReActAgent getReActAgent() {
        return ReActAgent.builder()
                .name("react-agent")
                .sysPrompt("请使用中文，简短的回答用户问题不得超过100字。")
                .model(openAIChatModel)
                .maxIters(agentProperties.getMaxIters())// 设置agent循环上线次数
                .maxRetries(agentProperties.getMaxRetries())// 设置模型调用最大尝试次数
                // 强制停止可能留下无结果的工具调用，下一问先补错误结果再继续
                .enablePendingToolRecovery(true)
                .stateStore(agentStateStore)
                .build();
    }

}
