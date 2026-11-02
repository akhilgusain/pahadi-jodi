package in.pahadijodi.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One stored, explainable scoring run. {@code reasons} is a JSON array of {code, points, description}. */
@Entity
@Table(name = "risk_evaluation")
public class RiskEvaluation {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private int score;
    @Column(nullable = false) private String action;
    @Column(nullable = false, columnDefinition = "TEXT") private String reasons;
    @Column(name = "trigger_type", nullable = false) private String triggerType;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected RiskEvaluation() {}

    public RiskEvaluation(UUID userId, int score, String action, String reasonsJson, String triggerType) {
        this.id = UUID.randomUUID();
        this.userId = userId; this.score = score; this.action = action; this.reasons = reasonsJson;
        this.triggerType = triggerType; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public int getScore() { return score; }
    public String getAction() { return action; }
    public String getReasons() { return reasons; }
    public String getTriggerType() { return triggerType; }
    public Instant getCreatedAt() { return createdAt; }
}
