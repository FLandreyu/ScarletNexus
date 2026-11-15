package dev.scarletnexus.ai;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class TutorAsyncConfiguration implements WebMvcConfigurer {
  @Bean
  public ThreadPoolTaskExecutor tutorExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(16);
    executor.setThreadNamePrefix("tutor-stream-");
    executor.setWaitForTasksToCompleteOnShutdown(false);
    return executor;
  }

  public void configureAsyncSupport(AsyncSupportConfigurer config) {
    config.setTaskExecutor(tutorExecutor());
    config.setDefaultTimeout(130000);
  }
}
