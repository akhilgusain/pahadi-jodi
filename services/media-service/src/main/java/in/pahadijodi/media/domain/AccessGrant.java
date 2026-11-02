package in.pahadijodi.media.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** "Show me your photos" request from viewer to owner (for ON_REQUEST privacy). */
@Entity
@Table(name = "access_grant")
public class AccessGrant {
    public enum Status { PENDING, GRANTED, DENIED }

    @Id private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(name = "viewer_id", nullable = false) private UUID viewerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected AccessGrant() {}
    public AccessGrant(UUID ownerId, UUID viewerId) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.viewerId = viewerId;
        this.status = Status.PENDING;
        this.createdAt = this.updatedAt = Instant.now();
    }
    public void decide(Status s) { this.status = s; this.updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getViewerId() { return viewerId; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
