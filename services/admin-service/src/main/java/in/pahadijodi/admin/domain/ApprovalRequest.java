package in.pahadijodi.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_request")
public class ApprovalRequest {
    public enum Kind { BAN, UNBAN }
    public enum Status { PENDING, APPROVED, REJECTED }

    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Kind kind;
    @Column(name = "target_user_id", nullable = false) private UUID targetUserId;
    @Column(name = "requested_by", nullable = false) private String requestedBy;
    @Column(nullable = false) private String reason;
    @Column(name = "duration_days") private Integer durationDays;
    @Column(nullable = false) private boolean permanent;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "decided_by") private String decidedBy;
    @Column(name = "decision_note") private String decisionNote;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "decided_at") private Instant decidedAt;

    protected ApprovalRequest() {}

    public ApprovalRequest(Kind kind, UUID target, String requestedBy, String reason, boolean permanent, Integer days) {
        this.id = UUID.randomUUID(); this.kind = kind; this.targetUserId = target; this.requestedBy = requestedBy;
        this.reason = reason; this.permanent = permanent; this.durationDays = days; this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }
    public void decide(Status s, String by, String note) {
        this.status = s; this.decidedBy = by; this.decisionNote = note; this.decidedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public Kind getKind() { return kind; }
    public UUID getTargetUserId() { return targetUserId; }
    public String getRequestedBy() { return requestedBy; }
    public String getReason() { return reason; }
    public Integer getDurationDays() { return durationDays; }
    public boolean isPermanent() { return permanent; }
    public Status getStatus() { return status; }
    public String getDecidedBy() { return decidedBy; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
