package in.pahadijodi.matchmaking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interest")
public class Interest {
    public enum Status { SENT, ACCEPTED, DECLINED, WITHDRAWN }

    @Id private UUID id;
    @Column(name = "from_user_id", nullable = false) private UUID fromUserId;
    @Column(name = "to_user_id", nullable = false) private UUID toUserId;
    @Column(name = "from_profile_id", nullable = false) private UUID fromProfileId;
    @Column(name = "to_profile_id", nullable = false) private UUID toProfileId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    private String message;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "responded_at") private Instant respondedAt;

    protected Interest() {}
    public Interest(UUID fromUserId, UUID toUserId, UUID fromProfileId, UUID toProfileId, String message) {
        this.id = UUID.randomUUID();
        this.fromUserId = fromUserId; this.toUserId = toUserId;
        this.fromProfileId = fromProfileId; this.toProfileId = toProfileId;
        this.message = message;
        this.status = Status.SENT;
        this.createdAt = Instant.now();
    }

    /** Re-send after a decline/withdrawal (decline cool-off is checked by the service). */
    public void resend(String message) {
        this.status = Status.SENT;
        this.message = message;
        this.createdAt = Instant.now();
        this.respondedAt = null;
    }
    public void respond(Status s) { this.status = s; this.respondedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getFromUserId() { return fromUserId; }
    public UUID getToUserId() { return toUserId; }
    public UUID getFromProfileId() { return fromProfileId; }
    public UUID getToProfileId() { return toProfileId; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRespondedAt() { return respondedAt; }
}
