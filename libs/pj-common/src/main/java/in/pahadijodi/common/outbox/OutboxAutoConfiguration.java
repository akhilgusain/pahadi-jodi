package in.pahadijodi.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * NOTE: services must scan the outbox package themselves (see each *Application class):
 * @EntityScan / @EnableJpaRepositories on {"in.pahadijodi.<svc>", "in.pahadijodi.common.outbox"}.
 */
@AutoConfiguration
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OutboxService outboxService(OutboxRepository repo, ObjectMapper mapper,
                                @Value("${spring.application.name}") String app) {
        return new OutboxService(repo, mapper, app);
    }

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnProperty(name = "pj.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
    OutboxRelay outboxRelay(OutboxRepository repo, KafkaTemplate<String, String> kafka) {
        return new OutboxRelay(repo, kafka);
    }
}
