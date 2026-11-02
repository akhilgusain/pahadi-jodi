package in.pahadijodi.moderation.domain;

import in.pahadijodi.moderation.domain.Enums.AppealStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appeal")
public class Appeal {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "case_id") private UUID caseId;
    @Column(nullable = false) private String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AppealStatus status;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decision_note") private String decisionNote;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;

    protected Appeal() {}
    public Appeal(UUID userId, UUID caseId, String message) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.caseId = caseId;
        this.message = message;
        this.status = AppealStatus.OPEN;
        this.createdAt = Instant.now();
    }
    public void decide(UUID by, boolean overturn, String note) {
        this.status = overturn ? AppealStatus.OVERTURNED : AppealStatus.UPHELD;
        this.decidedBy = by;
        this.decisionNote = note;
        this.resolvedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getCaseId() { return caseId; }
    public String getMessage() { return message; }
    public AppealStatus getStatus() { return status; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getCreatedAt() { return createdAt; }
}
