package com.hhxy.huazi.memory.config;

import com.alibaba.ttl.TtlRunnable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 仅为此处声明的执行器配置上下文任务装饰器，不会自动代理应用中的其他线程池。 */
@Configuration(proxyBeanMethods = false)
public class ExecutionContextConfiguration {
    // 两个 Bean 名称指向同一有界执行器，使用它提交的任务才受此处的上下文传播配置保护。
    @Bean(name = {"memoryExecutor", "memoryTaskExecutor"})
    public ThreadPoolTaskExecutor memoryTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        // 排队上限为 128；默认 AbortPolicy 在饱和时拒绝任务，不回退到调用者线程执行。
        executor.setQueueCapacity(128);
        // 在任务提交时捕获身份上下文，并在任务结束后恢复工作线程原上下文，防止串用身份。
        executor.setTaskDecorator(TtlRunnable::get);
        executor.setThreadNamePrefix("netagent-memory-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }

}
