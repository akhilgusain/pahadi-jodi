package in.pahadijodi.chat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableConfigurationProperties(ChatProperties.class)
public class AppConfig {
    /** Single TaskScheduler: used for STOMP heartbeats and for @Scheduled jobs (call ring timeout, outbox relay). */
    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        var s = new ThreadPoolTaskScheduler();
        s.setPoolSize(3);
        s.setThreadNamePrefix("chat-sched-");
        return s;
    }
}
