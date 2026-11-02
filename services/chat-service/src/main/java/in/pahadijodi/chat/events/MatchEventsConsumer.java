package in.pahadijodi.chat.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.chat.service.ChatService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** pj.match.created.v1 -> local match projection (unlocks chat) + conversation. */
@Component
public class MatchEventsConsumer {
    private static final Logger log = LoggerFactory.getLogger(MatchEventsConsumer.class);
    private final ChatService chat;
    private final ObjectMapper mapper;

    public MatchEventsConsumer(ChatService chat, ObjectMapper mapper) {
        this.chat = chat;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${pj.chat.topics.match-created}", autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void onMatchCreated(String json) {
        handle(json);
    }

    /** Package-visible for tests. Idempotent (redelivery safe); malformed events are logged and skipped. */
    public void handle(String json) {
        JsonNode payload;
        try {
            payload = mapper.readTree(json).path("payload");
        } catch (Exception e) {
            log.warn("Skipping unparsable match event: {}", e.toString());
            return;
        }
        UUID a = uuid(payload, "userAId", "userA");
        UUID b = uuid(payload, "userBId", "userB");
        if (a == null || b == null || a.equals(b)) {
            log.warn("Skipping match event without two distinct users");
            return;
        }
        chat.recordMatch(a, b, payload.path("matchId").asText(null));
    }

    private static UUID uuid(JsonNode n, String... names) {
        for (String name : names) {
            String v = n.path(name).asText(null);
            if (v != null) {
                try { return UUID.fromString(v); } catch (IllegalArgumentException ignored) { /* try next */ }
            }
        }
        return null;
    }
}
