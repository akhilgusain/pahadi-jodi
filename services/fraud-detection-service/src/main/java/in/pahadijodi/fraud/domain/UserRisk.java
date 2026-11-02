package in.pahadijodi.fraud.domain;

import in.pahadijodi.fraud.engine.Action;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_risk")
public class UserRisk {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(nullable = false) private int score;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Action action;
    /** Analyst override: wins over the automatic action until cleared. */
    @Enumerated(EnumType.STRING) @Column(name = "manual_action") private Action manualAction;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected UserRisk() {}
    public UserRisk(UUID userId) { this.userId = userId; this.action = Action.ALLOW; this.updatedAt = Instant.now(); }

    public void apply(int score, Action automatic) {
        this.score = score; this.action = automatic; this.updatedAt = Instant.now();
    }
    public void override(Action manual) { this.manualAction = manual; this.updatedAt = Instant.now(); }

    public Action effectiveAction() { return manualAction != null ? manualAction : action; }
    public UUID getUserId() { return userId; }
    public int getScore() { return score; }
    public Action getAction() { return action; }
    public Action getManualAction() { return manualAction; }
    public Instant getUpdatedAt() { return updatedAt; }
}
