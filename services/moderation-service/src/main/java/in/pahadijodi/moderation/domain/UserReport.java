package in.pahadijodi.moderation.domain;

import in.pahadijodi.moderation.domain.Enums.ReportReason;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_report")
public class UserReport {
    @Id private UUID id;
    @Column(name = "reporter_id", nullable = false) private UUID reporterId;
    @Column(name = "target_user_id", nullable = false) private UUID targetUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ReportReason reason;
    private String details;
    @Column(name = "evidence_ref") private String evidenceRef;
    @Column(name = "case_id") private UUID caseId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected UserReport() {}
    public UserReport(UUID reporter, UUID target, ReportReason reason, String details, String evidenceRef) {
        this.id = UUID.randomUUID();
        this.reporterId = reporter;
        this.targetUserId = target;
        this.reason = reason;
        this.details = details;
        this.evidenceRef = evidenceRef;
        this.createdAt = Instant.now();
    }
    public void attach(UUID caseId) { this.caseId = caseId; }
    public UUID getId() { return id; }
    public UUID getReporterId() { return reporterId; }
    public UUID getTargetUserId() { return targetUserId; }
    public ReportReason getReason() { return reason; }
    public UUID getCaseId() { return caseId; }
}
