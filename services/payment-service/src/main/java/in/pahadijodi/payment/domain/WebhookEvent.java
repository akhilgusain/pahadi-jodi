package in.pahadijodi.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Primary key = provider + ":" + event id, so a replayed event violates the PK (idempotency). */
@Entity
@Table(name = "webhook_event")
public class WebhookEvent {
    @Id private String id;
    @Column(nullable = false) private String provider;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(nullable = false) private String result;
    @Column(name = "process_error") private String processError;
    private String payload;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;

    protected WebhookEvent() {}
    public WebhookEvent(String provider, String eventId, String eventType, String result, String error, String payload) {
        this.id = provider + ":" + eventId; this.provider = provider; this.eventType = eventType; this.result = result;
        this.processError = error == null ? null : error.substring(0, Math.min(error.length(), 200));
        this.payload = payload == null ? null : payload.substring(0, Math.min(payload.length(), 8000));
        this.receivedAt = Instant.now();
    }
    public String getId() { return id; }
    public String getResult() { return result; }
}
