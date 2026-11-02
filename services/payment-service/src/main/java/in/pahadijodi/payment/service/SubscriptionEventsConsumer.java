package in.pahadijodi.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** A cancelled subscription must stop future auto-debits. */
@Component
public class SubscriptionEventsConsumer {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventsConsumer.class);
    private final MandateService mandates;
    private final ObjectMapper mapper;

    public SubscriptionEventsConsumer(MandateService mandates, ObjectMapper mapper) {
        this.mandates = mandates; this.mapper = mapper;
    }

    @KafkaListener(topics = "pj.subscription.cancelled.v1", groupId = "payment-service",
            autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void onCancelled(String json) {
        try {
            JsonNode payload = mapper.readTree(json).path("payload");
            String userId = payload.path("userId").asText(null);
            if (userId == null) { log.warn("subscription.cancelled without userId, skipped"); return; }
            int n = mandates.cancelAllFor(UUID.fromString(userId));
            log.info("Cancelled {} mandate(s) for user {}", n, userId);
        } catch (Exception e) {
            log.warn("Could not process subscription.cancelled event: {}", e.toString());   // defensive: never poison the consumer
        }
    }
}
