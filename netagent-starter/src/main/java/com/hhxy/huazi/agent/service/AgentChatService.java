package com.hhxy.huazi.agent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentChatService {

    void streamChat(SseEmitter sseEmitter, String question, String conversationId);
}
