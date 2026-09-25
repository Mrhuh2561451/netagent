package com.hhxy.huazi.memory.config;

import com.alibaba.ttl.TtlRunnable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class ExecutionContextConfiguration {
    @Bean(name = {"memoryExecutor", "memoryTaskExecutor"})
    public ThreadPoolTaskExecutor memoryTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(128);
        // 在任务提交时捕获身份上下文，并在任务结束后恢复工作线程原上下文，防止串用身份。
        executor.setTaskDecorator(TtlRunnable::get);
        executor.setThreadNamePrefix("netagent-memory-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }

}
