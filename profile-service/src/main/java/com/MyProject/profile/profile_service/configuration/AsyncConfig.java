package com.MyProject.profile.profile_service.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("AsyncThread-");
        executor.initialize();

        // Chuyển RequestContextHolder sang luồng con để Feign Interceptor lấy được token
        // Tuy nhiên RequestContextHolder.setAttributes cần được gọi thủ công hoặc dùng cấu hình riêng
        // Giải pháp chuẩn cho SecurityContext là DelegatingSecurityContextAsyncTaskExecutor
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }
}
