package in.pahadijodi.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Data-subject request. Erasure: REQUESTED --(grace elapses)--> COMPLETED, or --cancel--> CANCELED. */
@Entity
@Table(name = "data_request")
public class DataRequest {
    public enum Type { EXPORT, ERASURE }
    public enum Status { REQUESTED, CANCELED, COMPLETED }

    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Type type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "scheduled_for") private Instant scheduledFor;
    @Column(name = "completed_at") private Instant completedAt;

    protected DataRequest() {}
    public DataRequest(UUID userId, Type type, Instant scheduledFor) {
        this.id = UUID.randomUUID(); this.userId = userId; this.type = type; this.status = Status.REQUESTED;
        this.requestedAt = Instant.now(); this.scheduledFor = scheduledFor;
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getScheduledFor() { return scheduledFor; }
    public Instant getCompletedAt() { return completedAt; }
    public void cancel() {
        if (status != Status.REQUESTED) throw new IllegalStateException("Request is " + status);
        status = Status.CANCELED;
    }
    public void complete() {
        if (status != Status.REQUESTED) throw new IllegalStateException("Request is " + status);
        status = Status.COMPLETED; completedAt = Instant.now();
    }
}
