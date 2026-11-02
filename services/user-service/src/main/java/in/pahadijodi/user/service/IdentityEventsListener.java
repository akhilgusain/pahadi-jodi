package in.pahadijodi.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class IdentityEventsListener {
    private static final Logger log = LoggerFactory.getLogger(IdentityEventsListener.class);
    private final UserService users;
    private final ObjectMapper mapper;

    public IdentityEventsListener(UserService users, ObjectMapper mapper) {
        this.users = users; this.mapper = mapper;
    }

    @KafkaListener(topics = "pj.identity.user-registered.v1", autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void onUserRegistered(String message) {
        handle(message);
    }

    /** Public for tests. Never throws: a bad message must not block the partition. */
    public void handle(String message) {
        try {
            JsonNode userId = mapper.readTree(message).path("payload").path("userId");
            if (userId.isTextual()) users.ensureUser(UUID.fromString(userId.asText()));
            else log.warn("user-registered without payload.userId; ignored");
        } catch (Exception e) {
            log.warn("Ignoring malformed user-registered event: {}", e.toString());
        }
    }
}
