package in.pahadijodi.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private NotificationType type;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String body;
    @Column(name = "data_json", columnDefinition = "TEXT") private String dataJson;
    @Column(name = "dedupe_key", nullable = false) private String dedupeKey;
    @Column(nullable = false) private boolean inbox;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "read_at") private Instant readAt;

    protected Notification() {}

    public Notification(UUID userId, NotificationType type, String title, String body, String dataJson,
                        String dedupeKey, boolean inbox, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId; this.type = type; this.title = title; this.body = body; this.dataJson = dataJson;
        this.dedupeKey = dedupeKey; this.inbox = inbox; this.createdAt = now;
    }

    public void markRead(Instant at) { if (readAt == null) readAt = at; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getDataJson() { return dataJson; }
    public String getDedupeKey() { return dedupeKey; }
    public boolean isInbox() { return inbox; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
