package com.hhxy.huazi.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.hhxy.huazi.agent.config.ReActAgentProvider;
import com.hhxy.huazi.agent.service.AgentChatService;
import com.hhxy.huazi.common.context.UserContext;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.UncheckedIOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    private final ReActAgentProvider reActAgentProvider;


    @Override
    public void streamChat(SseEmitter sseEmitter, String question, String conversationId) {

        String userId = UserContext.getUserId();
        String actualConversationId = StrUtil.isBlank(conversationId)
                ? IdUtil.getSnowflakeNextIdStr()
                : conversationId;
        String taskId = IdUtil.getSnowflakeNextIdStr();
        try {
            startRun(question, userId, actualConversationId, taskId, sseEmitter);
        } catch (RuntimeException | Error e) {
            throw e;
        }
    }
    public  void startRun(String question, String userId, String actualConversationId, String taskId, SseEmitter sseEmitter){

        ReActAgent reActAgent = reActAgentProvider.getReActAgent();
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId(userId)
                .sessionId(actualConversationId)
                .build();
        Flux<AgentEvent> events = reActAgent.streamEvents(question,runtimeContext);
        events.subscribe(
                event -> {
                    if (event instanceof TextBlockDeltaEvent textEvent) {
                        String delta = textEvent.getDelta();
                        if (StrUtil.isNotEmpty(delta)) {
                            try {
                                sseEmitter.send(
                                        SseEmitter.event()
                                                .name("message")
                                                .data(delta)
                                                .id(actualConversationId)
                                                .comment("delta")

                                );
                            } catch (IOException e) {
                                log.error("sseEmitter 发送出错", e);
                                throw new UncheckedIOException(e);
                            }
                        }
                    }
                },
                sseEmitter::completeWithError,
                sseEmitter::complete
        );

    }
}

