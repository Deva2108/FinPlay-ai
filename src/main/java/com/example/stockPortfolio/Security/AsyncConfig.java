package com.example.stockPortfolio.Security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables @Async support and provides a small bounded executor used by
 * {@link com.example.stockPortfolio.AiManagement.service.InsightAsyncService}
 * for non-blocking insight generation.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "finplayAsyncExecutor")
    public Executor finplayAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("finplay-async-");
        executor.initialize();
        return executor;
    }
}
