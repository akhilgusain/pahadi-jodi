package in.pahadijodi.moderation.domain;

import in.pahadijodi.moderation.domain.Enums.*;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moderation_case")
public class ModerationCase {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(name = "content_type", nullable = false) private ContentType contentType;
    @Column(name = "ref_id", nullable = false) private String refId;
    @Column(name = "subject_user_id") private UUID subjectUserId;
    @Column(nullable = false) private String source;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Decision decision;
    @Column(nullable = false) private String labels;
    @Column(nullable = false) private double score;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CaseStatus status;
    @Column(nullable = false) private int priority;
    @Column(name = "assigned_to") private UUID assignedTo;
    @Column(name = "due_at", nullable = false) private Instant dueAt;
    @Enumerated(EnumType.STRING) private Resolution resolution;
    @Column(name = "resolved_by") private UUID resolvedBy;
    @Column(name = "resolved_at") private Instant resolvedAt;
    private String note;
    private String excerpt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ModerationCase() {}

    public ModerationCase(ContentType type, String refId, UUID subject, String source, Decision decision,
                          String labels, double score, int priority, String excerpt) {
        this.id = UUID.randomUUID();
        this.contentType = type;
        this.refId = refId;
        this.subjectUserId = subject;
        this.source = source;
        this.decision = decision;
        this.labels = labels;
        this.score = score;
        this.status = CaseStatus.OPEN;
        this.priority = priority;
        this.excerpt = excerpt;
        this.createdAt = this.updatedAt = Instant.now();
        this.dueAt = createdAt.plus(slaFor(priority));
    }

    /** SLA by priority: 1 = 4h, 2 = 12h, 3 = 24h. */
    public static Duration slaFor(int priority) {
        return Duration.ofHours(priority <= 1 ? 4 : priority == 2 ? 12 : 24);
    }

    public boolean isFinal() { return status == CaseStatus.RESOLVED; }

    public void assign(UUID moderator) {
        this.assignedTo = moderator;
        this.status = CaseStatus.IN_REVIEW;
        this.updatedAt = Instant.now();
    }

    public void escalate(UUID by, String note) {
        this.status = CaseStatus.ESCALATED;
        this.assignedTo = null;
        this.priority = 1;
        this.dueAt = Instant.now().plus(slaFor(1));
        this.note = note;
        this.updatedAt = Instant.now();
    }

    public void resolve(Resolution r, UUID by, String note) {
        this.status = CaseStatus.RESOLVED;
        this.resolution = r;
        this.resolvedBy = by;
        this.resolvedAt = Instant.now();
        this.note = note;
        this.updatedAt = this.resolvedAt;
    }

    public void bumpPriority(int p) {
        if (p < priority) {
            this.priority = p;
            Instant newDue = createdAt.plus(slaFor(p));
            if (newDue.isBefore(dueAt)) this.dueAt = newDue;
            this.updatedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public ContentType getContentType() { return contentType; }
    public String getRefId() { return refId; }
    public UUID getSubjectUserId() { return subjectUserId; }
    public String getSource() { return source; }
    public Decision getDecision() { return decision; }
    public String getLabels() { return labels; }
    public double getScore() { return score; }
    public CaseStatus getStatus() { return status; }
    public int getPriority() { return priority; }
    public UUID getAssignedTo() { return assignedTo; }
    public Instant getDueAt() { return dueAt; }
    public Resolution getResolution() { return resolution; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getNote() { return note; }
    public String getExcerpt() { return excerpt; }
    public Instant getCreatedAt() { return createdAt; }
}
