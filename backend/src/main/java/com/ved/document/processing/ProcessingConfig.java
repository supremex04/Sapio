package com.ved.document.processing;

import com.ved.document.DocumentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded in-process worker pool for document conversion. The pool size caps concurrent calls to the
 * inference service. Replace with a message broker when processing must survive across instances.
 */
@Configuration
@EnableAsync
@EnableScheduling
class ProcessingConfig {

    static final String EXECUTOR = "documentProcessingExecutor";

    @Bean(EXECUTOR)
    ThreadPoolTaskExecutor documentProcessingExecutor(DocumentProperties properties) {
        DocumentProperties.Processing config = properties.processing();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("doc-proc-");
        executor.setCorePoolSize(config.corePoolSize());
        executor.setMaxPoolSize(config.maxPoolSize());
        executor.setQueueCapacity(config.queueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
