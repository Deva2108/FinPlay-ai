package com.example.stockPortfolio.Security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Enables @Async support backed by Java 21 virtual threads.
 *
 * @Async tasks are predominantly IO-bound (Groq/Gemini LLM calls, Finnhub/News
 * REST). A bounded platform pool (core=2, max=4) becomes a hard throughput
 * cap under burst load. Virtual threads scale to thousands of concurrent
 * IO-blocked workers at near-zero memory/scheduling cost, while preserving
 * synchronous code style. spring.threads.virtual.enabled=true (set in
 * application-prod.properties) makes Tomcat use virtual threads too.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "finplayAsyncExecutor")
    public AsyncTaskExecutor finplayAsyncExecutor() {
        return new TaskExecutorAdapter(
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name("finplay-async-", 0).factory()
                )
        );
    }

    @Override
    public Executor getAsyncExecutor() {
        return finplayAsyncExecutor();
    }
}
