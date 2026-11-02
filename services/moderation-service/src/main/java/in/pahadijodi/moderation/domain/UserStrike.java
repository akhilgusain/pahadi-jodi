package in.pahadijodi.moderation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_strike")
public class UserStrike {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "case_id") private UUID caseId;
    @Column(nullable = false) private int points;
    private String reason;
    @Column(nullable = false) private boolean revoked;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected UserStrike() {}
    public UserStrike(UUID userId, UUID caseId, int points, String reason) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.caseId = caseId;
        this.points = points;
        this.reason = reason;
        this.createdAt = Instant.now();
    }
    public void revoke() { this.revoked = true; }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getCaseId() { return caseId; }
    public int getPoints() { return points; }
    public boolean isRevoked() { return revoked; }
}
