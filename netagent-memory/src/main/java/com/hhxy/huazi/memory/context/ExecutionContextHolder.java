package com.hhxy.huazi.memory.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.alibaba.ttl.threadpool.TtlExecutors;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** 线程池会复用工作线程，身份传递必须包装任务，不能仅依赖 ThreadLocal 的父子线程继承。 */
public final class ExecutionContextHolder {
    private static final TransmittableThreadLocal<ExecutionContext> CONTEXT = new TransmittableThreadLocal<>() {
        @Override
        protected ExecutionContext childValue(ExecutionContext parentValue) {
            // 工作线程不继承创建者的身份，仅通过任务包装捕获和恢复上下文。
            return null;
        }
    };

    private ExecutionContextHolder() {}

    // 缺少身份上下文时立即失败，不回退到默认租户或匿名身份。
    public static ExecutionContext require() {
        ExecutionContext context = CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("缺少执行上下文");
        }
        return context;
    }

    // 仅覆盖当前线程身份，不负责恢复；调用方须在 finally 中清理，嵌套作用域应使用 callWith。
    public static void set(ExecutionContext context) {
        CONTEXT.set(Objects.requireNonNull(context, "执行上下文不能为空"));
    }

    // 移除当前线程身份但不恢复外层值，供入口调用方在任务结束时清理，避免线程复用串用身份。
    public static void clear() {
        CONTEXT.remove();
    }

    // 在给定身份下同步执行，无论正常返回还是抛异常都恢复外层身份；无外层身份则清理。
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

    // 返回的执行器在每次提交任务时捕获上下文，结束后恢复工作线程原值；不是在调用 wrap 时固定身份。
    public static Executor wrap(Executor executor) {
        return TtlExecutors.getTtlExecutor(Objects.requireNonNull(executor, "执行器不能为空"));
    }
}
