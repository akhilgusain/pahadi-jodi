package in.pahadijodi.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Deliveries that exhausted their retries (or failed permanently). Inspect and replay manually / via ops tooling. */
@Entity
@Table(name = "dead_letter")
public class DeadLetter {
    @Id private UUID id;
    @Column(name = "delivery_id", nullable = false) private UUID deliveryId;
    @Column(name = "notification_id", nullable = false) private UUID notificationId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Channel channel;
    @Column(nullable = false, columnDefinition = "TEXT") private String payload;
    private String error;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected DeadLetter() {}
    public DeadLetter(DeliveryLog d, String payload, String error, Instant now) {
        this.id = UUID.randomUUID();
        this.deliveryId = d.getId(); this.notificationId = d.getNotificationId(); this.userId = d.getUserId();
        this.channel = d.getChannel(); this.payload = payload; this.error = error; this.createdAt = now;
    }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getUserId() { return userId; }
    public Channel getChannel() { return channel; }
    public String getError() { return error; }
}
