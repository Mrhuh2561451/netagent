package com.hhxy.huazi.agent.conversation;

import com.hhxy.huazi.memory.context.ExecutionContextHolder;
import com.hhxy.huazi.memory.model.CommitResult;
import com.hhxy.huazi.memory.model.MemoryMessage;
import com.hhxy.huazi.memory.model.MemoryScope;
import com.hhxy.huazi.memory.model.MemorySnapshot;
import com.hhxy.huazi.memory.model.MemoryTurn;
import com.hhxy.huazi.memory.model.MemoryValidationException;
import com.hhxy.huazi.memory.service.ConversationMemoryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ConversationRunner {
    private final ConversationMemoryService memory;

    public ConversationRunner(ConversationMemoryService memory) {
        this.memory = Objects.requireNonNull(memory, "Conversation memory is required");
    }

    public Result run(MemoryScope scope, String requestId, String input, TurnGenerator generator) throws Exception {
        try {
            checkInterrupted();
            Objects.requireNonNull(scope, "Memory scope is required");
            MemoryScope.requireId(requestId);
            if (input == null || input.isBlank()) {
                throw new IllegalArgumentException("User input is required");
            }
            Objects.requireNonNull(generator, "Turn generator is required");
            var context = ExecutionContextHolder.require();
            // 这里只检查可信调用边界的身份一致性，不替代真实授权。
            if (!context.tenantId().equals(scope.tenantId()) || !context.userId().equals(scope.userId())) {
                throw new SecurityException("Execution identity does not match memory scope");
            }

            MemorySnapshot history = memory.loadOrCreate(scope).snapshot();
            // 重复检测仅覆盖当前记忆窗口，不保证跨过期的全生命周期幂等。
            if (history.turns().stream().anyMatch(turn -> turn.requestId().equals(requestId))) {
                throw new IllegalStateException("Request already exists in the current memory window");
            }
            checkInterrupted();
            MemoryMessage userInput = new MemoryMessage(UUID.randomUUID().toString(),
                    MemoryMessage.Role.USER, input, null, List.of());
            List<MemoryMessage> generated = generator.generate(history, userInput);
            if (generated == null) {
                throw new MemoryValidationException("Generated messages are required");
            }
            var messages = new ArrayList<MemoryMessage>();
            messages.add(userInput);
            messages.addAll(generated);
            MemoryTurn turn = new MemoryTurn(requestId, messages, Instant.now());
            checkInterrupted();
            CommitResult commitResult = memory.commitTurn(scope, history.revision(), turn);
            return new Result(history.revision(), turn, commitResult);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Conversation execution interrupted");
        }
    }

    // 系统提示词、令牌预算和工具权限由回调外层负责，回调仅返回助手和工具消息。
    @FunctionalInterface
    public interface TurnGenerator {
        List<MemoryMessage> generate(MemorySnapshot history, MemoryMessage userInput) throws Exception;
    }

    // 调用方重试记忆提交时应复用固定轮次和原版本，不重新执行生成。
    public record Result(String expectedRevision, MemoryTurn turn, CommitResult commitResult) {
        public Result {
            MemorySnapshot.requireRevision(expectedRevision);
            Objects.requireNonNull(turn, "Memory turn is required");
            Objects.requireNonNull(commitResult, "Commit result is required");
        }

        public boolean memoryCommitted() {
            return commitResult.status() == CommitResult.Status.COMMITTED
                    || commitResult.status() == CommitResult.Status.ALREADY_COMMITTED;
        }

        @Override
        public String toString() {
            return "Result[expectedRevision=" + expectedRevision + ", messageCount=" + turn.messages().size()
                    + ", commitStatus=" + commitResult.status() + "]";
        }
    }
}
