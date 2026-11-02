package in.pahadijodi.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One row per (notification, external channel): the record of what was sent, suppressed or retried. */
@Entity
@Table(name = "delivery_log")
public class DeliveryLog {
    @Id private UUID id;
    @Column(name = "notification_id", nullable = false) private UUID notificationId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Channel channel;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DeliveryStatus status;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "last_error") private String lastError;
    @Column(name = "suppressed_reason") private String suppressedReason;
    @Column(name = "provider_ref") private String providerRef;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Version private long version;

    protected DeliveryLog() {}

    private DeliveryLog(UUID notificationId, UUID userId, Channel channel, DeliveryStatus status, Instant now) {
        this.id = UUID.randomUUID();
        this.notificationId = notificationId; this.userId = userId; this.channel = channel;
        this.status = status; this.createdAt = now;
    }

    public static DeliveryLog pending(UUID notificationId, UUID userId, Channel ch, Instant dueAt, Instant now) {
        DeliveryLog d = new DeliveryLog(notificationId, userId, ch, DeliveryStatus.PENDING, now);
        d.nextAttemptAt = dueAt;
        return d;
    }

    public static DeliveryLog suppressed(UUID notificationId, UUID userId, Channel ch, String reason, Instant now) {
        DeliveryLog d = new DeliveryLog(notificationId, userId, ch, DeliveryStatus.SUPPRESSED, now);
        d.suppressedReason = reason;
        return d;
    }

    public void suppress(String reason) { status = DeliveryStatus.SUPPRESSED; suppressedReason = reason; nextAttemptAt = null; }

    public void sent(String providerRef, Instant now) {
        attempts++; status = DeliveryStatus.SENT; this.providerRef = providerRef; sentAt = now; nextAttemptAt = null; lastError = null;
    }

    public void retryLater(String error, Instant next) {
        attempts++; lastError = truncate(error); nextAttemptAt = next;
    }

    public void dead(String error) {
        attempts++; status = DeliveryStatus.DEAD; lastError = truncate(error); nextAttemptAt = null;
    }

    private static String truncate(String s) { return s == null ? null : s.length() > 480 ? s.substring(0, 480) : s; }

    public UUID getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public UUID getUserId() { return userId; }
    public Channel getChannel() { return channel; }
    public DeliveryStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public String getSuppressedReason() { return suppressedReason; }
    public String getProviderRef() { return providerRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}
