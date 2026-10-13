package com.sapio.document.processing;

import com.sapio.common.web.RequestId;
import com.sapio.document.DocumentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded in-process worker pool for document conversion. The pool size caps concurrent calls to the
 * inference service per instance. Several instances share the work safely through the database (atomic claim,
 * heartbeat lease); see {@link DocumentProcessingTriggers}. A message broker can replace the triggers later
 * without touching {@link DocumentProcessor}.
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
        // Jobs log under the request id that started them (an upload), or their own (scheduled retries).
        executor.setTaskDecorator(RequestId.propagatingDecorator());
        return executor;
    }
}
