package in.pahadijodi.admin.audit;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import org.hibernate.annotations.Immutable;

/**
 * One immutable audit row. No setters; Hibernate never issues UPDATEs for it ({@link Immutable}) and the repository
 * exposes no delete. Tampering at the DB level is detected by {@link AuditChain}.
 */
@Entity
@Immutable
@Table(name = "admin_audit_log")
public class AuditLog {
    public static final String GENESIS = "0".repeat(64);

    @Id private long seq;
    @Column(name = "actor_id", nullable = false) private String actorId;
    @Column(name = "actor_roles") private String actorRoles;
    @Column(nullable = false) private String action;
    @Column(name = "target_type") private String targetType;
    @Column(name = "target_id") private String targetId;
    @Column(nullable = false, columnDefinition = "TEXT") private String details;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "prev_hash", nullable = false) private String prevHash;
    @Column(nullable = false) private String hash;

    protected AuditLog() {}

    private AuditLog(long seq, String actorId, String actorRoles, String action, String targetType, String targetId,
                     String details, Instant createdAt, String prevHash) {
        this.seq = seq; this.actorId = actorId; this.actorRoles = actorRoles; this.action = action;
        this.targetType = targetType; this.targetId = targetId; this.details = details;
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);   // millisecond precision survives every DB round trip
        this.prevHash = prevHash;
        this.hash = computeHash();
    }

    /** Builds the next entry of the chain; the hash covers every field plus the previous hash. */
    public static AuditLog next(long prevSeq, String prevHash, String actorId, String actorRoles, String action,
                                String targetType, String targetId, String detailsJson, Instant at) {
        return new AuditLog(prevSeq + 1, actorId, actorRoles, action, targetType, targetId, detailsJson, at, prevHash);
    }

    public String computeHash() {
        String canonical = String.join("|", Long.toString(seq), n(actorId), n(actorRoles), n(action), n(targetType), n(targetId),
                n(details), Long.toString(createdAt.toEpochMilli()), n(prevHash));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String n(String s) { return s == null ? "" : s; }

    public long getSeq() { return seq; }
    public String getActorId() { return actorId; }
    public String getActorRoles() { return actorRoles; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPrevHash() { return prevHash; }
    public String getHash() { return hash; }
}
