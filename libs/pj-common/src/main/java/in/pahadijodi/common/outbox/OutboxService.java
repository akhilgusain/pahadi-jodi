package in.pahadijodi.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.event.EventEnvelope;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Call from inside a business transaction; the event commits atomically with your data. */
public class OutboxService {
    private final OutboxRepository repo;
    private final ObjectMapper mapper;
    private final String producer;

    public OutboxService(OutboxRepository repo, ObjectMapper mapper,
                         @Value("${spring.application.name}") String producer) {
        this.repo = repo;
        this.mapper = mapper;
        this.producer = producer;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, String eventType, String partitionKey, Object payload) {
        UUID id = UUID.randomUUID();
        var env = new EventEnvelope(id, eventType, 1, Instant.now(), producer, null, partitionKey, payload);
        try {
            repo.save(new OutboxEvent(id, topic, partitionKey, mapper.writeValueAsString(env)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + eventType, e);
        }
    }
}
