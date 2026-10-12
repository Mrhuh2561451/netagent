package com.hhxy.huazi.agent.controller;

import com.hhxy.huazi.agent.config.AgentProperties;
import com.hhxy.huazi.agent.service.AgentChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class AgentChatController {

    private final AgentProperties agentProperties;

    private final AgentChatService agentChatService;

    @PostMapping("/chat/stream")
    public SseEmitter streamChat(@RequestParam String question,@RequestParam(required = false)String conversationId){
        SseEmitter sseEmitter = new SseEmitter(agentProperties.getSseTimeoutMs());
        agentChatService.streamChat(sseEmitter, question, conversationId);
        return sseEmitter;

    }
}
