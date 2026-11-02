package in.pahadijodi.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.analytics.mapping.EventMapper;
import in.pahadijodi.analytics.model.FactRow;
import in.pahadijodi.analytics.store.AnalyticsStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Single entry point for Kafka and the dev endpoint: parse the envelope defensively, map, de-duplicate, store. */
@Service
public class IngestService {
    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    public record Result(int stored, int duplicates, int ignored) {
        Result plus(Result o) { return new Result(stored + o.stored, duplicates + o.duplicates, ignored + o.ignored); }
    }

    private final EventMapper mapper;
    private final AnalyticsStore store;
    private final ObjectMapper json;

    public IngestService(EventMapper mapper, AnalyticsStore store, ObjectMapper json) {
        this.mapper = mapper;
        this.store = store;
        this.json = json;
    }

    /** @param message a JSON EventEnvelope (or a bare payload object); never throws on bad input */
    public Result ingest(String topic, String message) {
        if (EventMapper.ignoredTopic(topic)) return new Result(0, 0, 1);
        try {
            JsonNode root = json.readTree(message);
            if (root == null || !root.isObject()) return new Result(0, 0, 1);
            return ingest(topic, root);
        } catch (Exception e) {
            log.warn("Skipping malformed event on {}: {}", topic, e.toString());
            return new Result(0, 0, 1);
        }
    }

    public Result ingest(String topic, JsonNode root) {
        JsonNode payload = root.has("payload") && root.get("payload").isObject() ? root.get("payload") : root;
        Instant at = parseInstant(root.path("occurredAt").asText(null));
        String eventId = root.hasNonNull("eventId") ? root.get("eventId").asText()
                : UUID.nameUUIDFromBytes((topic + ":" + payload).getBytes(StandardCharsets.UTF_8)).toString();
        Result total = new Result(0, 0, 0);
        for (FactRow row : mapper.map(topic, eventId, at, payload)) {
            total = total.plus(store.append(row) ? new Result(1, 0, 0) : new Result(0, 1, 0));
        }
        return total;
    }

    private static Instant parseInstant(String s) {
        try { return s == null ? Instant.now() : Instant.parse(s); } catch (Exception e) { return Instant.now(); }
    }
}
