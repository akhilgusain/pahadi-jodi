package in.pahadijodi.media.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per (user, type). Never holds a government ID number: only an HMAC of it (duplicate
 * detection) and the last 4 characters (display).
 */
@Entity
@Table(name = "verification_check")
public class VerificationCheck {
    public enum Type { PAN, AADHAAR, SELFIE }
    public enum Status { PENDING, VERIFIED, MANUAL_REVIEW, REJECTED }

    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Type type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "provider_ref") private String providerRef;
    @Column(name = "id_hash") private String idHash;
    @Column(name = "id_last4") private String idLast4;
    private Double score;
    private String reason;
    @Column(nullable = false) private int attempts;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected VerificationCheck() {}
    public VerificationCheck(UUID userId, Type type) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.status = Status.PENDING;
        this.createdAt = this.updatedAt = Instant.now();
    }

    public void attempt() { this.attempts++; this.updatedAt = Instant.now(); }

    public void set(Status s, String reason, Double score) {
        this.status = s;
        this.reason = reason;
        this.score = score;
        this.updatedAt = Instant.now();
    }
    public void setProviderRef(String r) { this.providerRef = r; }
    public void setIdentity(String hash, String last4) { this.idHash = hash; this.idLast4 = last4; }
    public void review(UUID reviewer, Status s, String reason) {
        this.reviewedBy = reviewer;
        set(s, reason, this.score);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public String getProviderRef() { return providerRef; }
    public String getIdHash() { return idHash; }
    public String getIdLast4() { return idLast4; }
    public Double getScore() { return score; }
    public String getReason() { return reason; }
    public int getAttempts() { return attempts; }
    public Instant getUpdatedAt() { return updatedAt; }
}
