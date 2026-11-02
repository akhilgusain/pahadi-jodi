package in.pahadijodi.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    public enum Status { ACTIVE, DEACTIVATED, PENDING_DELETION, DELETED }

    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "deletion_scheduled_at") private Instant deletionScheduledAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected AppUser() {}
    public AppUser(UUID id) {
        this.id = id;
        this.status = Status.ACTIVE;
        this.createdAt = this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Status getStatus() { return status; }
    public Instant getDeletionScheduledAt() { return deletionScheduledAt; }
    public Instant getCreatedAt() { return createdAt; }

    // ---- account status state machine ----
    public void deactivate() {
        requireStatus(Status.ACTIVE, "Only an active account can be deactivated");
        move(Status.DEACTIVATED);
    }
    public void reactivate() {
        requireStatus(Status.DEACTIVATED, "Only a deactivated account can be reactivated");
        move(Status.ACTIVE);
    }
    public void scheduleDeletion(Instant at) {
        if (status != Status.ACTIVE && status != Status.DEACTIVATED) {
            throw new IllegalStateException("Cannot schedule deletion from " + status);
        }
        this.deletionScheduledAt = at;
        move(Status.PENDING_DELETION);
    }
    public void cancelDeletion() {
        requireStatus(Status.PENDING_DELETION, "No deletion pending");
        this.deletionScheduledAt = null;
        move(Status.ACTIVE);
    }
    public void markDeleted() {
        requireStatus(Status.PENDING_DELETION, "No deletion pending");
        move(Status.DELETED);
    }
    private void requireStatus(Status s, String msg) { if (status != s) throw new IllegalStateException(msg); }
    private void move(Status s) { this.status = s; this.updatedAt = Instant.now(); }
}
