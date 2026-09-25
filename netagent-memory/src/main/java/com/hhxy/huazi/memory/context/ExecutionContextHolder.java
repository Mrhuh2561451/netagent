package com.hhxy.huazi.memory.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.alibaba.ttl.threadpool.TtlExecutors;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class ExecutionContextHolder {
    private static final TransmittableThreadLocal<ExecutionContext> CONTEXT = new TransmittableThreadLocal<>() {
        @Override
        protected ExecutionContext childValue(ExecutionContext parentValue) {
            // 工作线程不继承创建者的身份，仅通过任务包装捕获和恢复上下文。
            return null;
        }
    };

    private ExecutionContextHolder() {}

    public static ExecutionContext require() {
        ExecutionContext context = CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("缺少执行上下文");
        }
        return context;
    }

    public static void set(ExecutionContext context) {
        CONTEXT.set(Objects.requireNonNull(context, "执行上下文不能为空"));
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static <T> T callWith(ExecutionContext context, Supplier<T> action) {
        Objects.requireNonNull(action, "执行动作不能为空");
        ExecutionContext previous = CONTEXT.get();
        set(context);
        try {
            return action.get();
        } finally {
            // 嵌套调用须恢复外层身份，无外层上下文时清理线程以避免身份泄漏。
            if (previous == null) {
                clear();
            } else {
                CONTEXT.set(previous);
            }
        }
    }

    public static Executor wrap(Executor executor) {
        return TtlExecutors.getTtlExecutor(Objects.requireNonNull(executor, "执行器不能为空"));
    }
}
