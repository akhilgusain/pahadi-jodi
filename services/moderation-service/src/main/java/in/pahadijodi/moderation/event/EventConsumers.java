package in.pahadijodi.moderation.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.moderation.service.EventHandler;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventConsumers {
    private static final Logger log = LoggerFactory.getLogger(EventConsumers.class);
    private final EventHandler handler;
    private final ObjectMapper mapper;

    public EventConsumers(EventHandler handler, ObjectMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "pj.media.uploaded.v1", groupId = "moderation-service", autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void media(String m) { run("media", m, handler::onMediaUploaded); }

    @KafkaListener(topics = "pj.chat.message-sent.v1", groupId = "moderation-service", autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void chat(String m) { run("chat", m, handler::onChatMessage); }

    @KafkaListener(topics = "pj.profile.updated.v1", groupId = "moderation-service", autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void profile(String m) { run("profile", m, handler::onProfileUpdated); }

    /** Never throw: a bad message must not block the partition (poison-pill protection). */
    private void run(String kind, String message, Consumer<JsonNode> action) {
        try {
            action.accept(mapper.readTree(message));
        } catch (Exception e) {
            log.warn("Skipping {} event that could not be processed: {}", kind, e.toString());
        }
    }
}
