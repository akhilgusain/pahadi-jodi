package in.pahadijodi.moderation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Append-only. @Immutable makes Hibernate refuse updates; the repository exposes no delete either. */
@Entity
@Immutable
@Table(name = "decision_audit")
public class DecisionAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long seq;
    @Column(name = "case_id") private UUID caseId;
    @Column(nullable = false) private String actor;
    @Column(nullable = false) private String action;
    private String detail;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "prev_hash", nullable = false) private String prevHash;
    @Column(nullable = false) private String hash;

    protected DecisionAudit() {}
    public DecisionAudit(UUID caseId, String actor, String action, String detail, Instant createdAt, String prevHash, String hash) {
        this.caseId = caseId;
        this.actor = actor;
        this.action = action;
        this.detail = detail;
        this.createdAt = createdAt;
        this.prevHash = prevHash;
        this.hash = hash;
    }
    public Long getSeq() { return seq; }
    public UUID getCaseId() { return caseId; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPrevHash() { return prevHash; }
    public String getHash() { return hash; }
}
