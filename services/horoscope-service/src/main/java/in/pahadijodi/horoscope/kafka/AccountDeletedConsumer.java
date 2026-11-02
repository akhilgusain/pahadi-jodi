package in.pahadijodi.horoscope.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.horoscope.service.HoroscopeService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** DPDP erasure: when an account is deleted, birth data and the user to chart link are removed (charts are anonymous and stay). */
@Component
public class AccountDeletedConsumer {
    private static final Logger log = LoggerFactory.getLogger(AccountDeletedConsumer.class);
    private final HoroscopeService service;
    private final ObjectMapper json;

    public AccountDeletedConsumer(HoroscopeService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @KafkaListener(topics = "pj.user.account-deleted.v1", groupId = "horoscope-service",
            autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void onAccountDeleted(String message) {
        try {
            JsonNode payload = json.readTree(message).path("payload");
            String id = payload.path("userId").asText(null);
            if (id == null) { log.warn("account-deleted event without userId, skipped"); return; }
            service.erase(UUID.fromString(id));
        } catch (Exception e) {
            log.warn("Skipping malformed account-deleted event: {}", e.toString());
        }
    }
}
