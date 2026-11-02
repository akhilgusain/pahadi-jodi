package in.pahadijodi.common.event;

import java.time.Instant;
import java.util.UUID;

/** Standard envelope for every Kafka event (see docs/05). Payload is serialized JSON. */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String producer,
        String traceId,
        String partitionKey,
        Object payload) {
}
