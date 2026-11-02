package in.pahadijodi.fraud.domain;

import in.pahadijodi.fraud.engine.Action;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_case")
public class FraudCase {
    public enum Status { OPEN, RESOLVED }
    /** Analyst feedback, kept as training labels. */
    public enum Label { TRUE_POSITIVE, FALSE_POSITIVE }

    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(nullable = false) private int score;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Action action;
    @Enumerated(EnumType.STRING) private Label label;
    private String notes;
    @Column(name = "resolved_by") private UUID resolvedBy;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "opened_at", nullable = false) private Instant openedAt;

    protected FraudCase() {}
    public FraudCase(UUID userId, int score, Action action) {
        this.id = UUID.randomUUID(); this.userId = userId; this.score = score; this.action = action;
        this.status = Status.OPEN; this.openedAt = Instant.now();
    }
    public void update(int score, Action action) { this.score = score; this.action = action; }
    public void resolve(Label label, String notes, UUID by) {
        this.status = Status.RESOLVED; this.label = label; this.notes = notes; this.resolvedBy = by; this.resolvedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Status getStatus() { return status; }
    public int getScore() { return score; }
    public Action getAction() { return action; }
    public Label getLabel() { return label; }
    public String getNotes() { return notes; }
    public UUID getResolvedBy() { return resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getOpenedAt() { return openedAt; }
}
