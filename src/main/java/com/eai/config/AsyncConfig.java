package com.eai.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AsyncConfig {

    @Value("${eai.execution.thread-pool.core-size:4}")
    private int core;

    @Value("${eai.execution.thread-pool.max-size:16}")
    private int max;

    @Value("${eai.execution.thread-pool.queue-capacity:100}")
    private int queue;

    @Bean(name = "eaiExecutor")
    public Executor eaiExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(core);
        e.setMaxPoolSize(max);
        e.setQueueCapacity(queue);
        e.setThreadNamePrefix("eai-exec-");
        e.initialize();
        return e;
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(4);
        s.setThreadNamePrefix("eai-sched-");
        s.setRemoveOnCancelPolicy(true);
        s.initialize();
        return s;
    }
}
