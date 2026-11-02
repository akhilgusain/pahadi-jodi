package in.pahadijodi.moderation.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_event")
public class ProcessedEvent {
    @Id @Column(name = "event_id") private String eventId;
    @Column(name = "processed_at", nullable = false) private Instant processedAt;
    protected ProcessedEvent() {}
    public ProcessedEvent(String eventId) { this.eventId = eventId; this.processedAt = Instant.now(); }
    public String getEventId() { return eventId; }
}
