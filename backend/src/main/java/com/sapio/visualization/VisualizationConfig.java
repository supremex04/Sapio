package com.sapio.visualization;

import com.sapio.common.web.RequestId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Visualizations run on their own small pool: a render takes up to minutes and the renderer draws one at a time per
 * instance, so a few workers are enough and document processing is never held up behind them.
 */
@Configuration
class VisualizationConfig {

    static final String EXECUTOR = "visualizationExecutor";

    @Bean(EXECUTOR)
    ThreadPoolTaskExecutor visualizationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("viz-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setTaskDecorator(RequestId.propagatingDecorator());
        return executor;
    }
}
