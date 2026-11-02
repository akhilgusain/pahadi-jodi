package in.pahadijodi.media.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.media.domain.MatchLink;
import in.pahadijodi.media.domain.MatchLinkRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps a local copy of mutual matches so MATCHES_ONLY media checks need no cross-service call. */
@Component
public class MatchEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(MatchEventConsumer.class);
    private static final String[][] FIELD_PAIRS = {
            {"userAId", "userBId"}, {"userIdA", "userIdB"}, {"userA", "userB"}, {"userId", "otherUserId"}, {"fromUserId", "toUserId"}};
    private final MatchLinkRepository links;
    private final ObjectMapper mapper;

    public MatchEventConsumer(MatchLinkRepository links, ObjectMapper mapper) {
        this.links = links;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "pj.match.created.v1", groupId = "media-service",
            autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void onMatch(String message) {
        try {
            handle(mapper.readTree(message));
        } catch (Exception e) {
            log.warn("Ignoring unreadable match event: {}", e.toString());   // never poison the partition
        }
    }

    @Transactional
    public void handle(JsonNode envelope) {
        JsonNode p = envelope.path("payload");
        for (String[] f : FIELD_PAIRS) {
            if (p.hasNonNull(f[0]) && p.hasNonNull(f[1])) {
                UUID a = UUID.fromString(p.get(f[0]).asText()), b = UUID.fromString(p.get(f[1]).asText());
                link(a, b);
                link(b, a);
                return;
            }
        }
        log.warn("match event without recognisable user ids: {}", p);
    }

    private void link(UUID a, UUID b) {
        if (!links.existsByUserAAndUserB(a, b)) links.save(new MatchLink(a, b));
    }
}
