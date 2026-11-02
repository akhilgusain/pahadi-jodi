package in.pahadijodi.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Append-only consent ledger (DPDP Act 2023): the latest row per type is the current state. */
@Entity
@Table(name = "consent")
public class Consent {
    public enum Type { TERMS, PRIVACY, MARKETING }

    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Type type;
    @Column(nullable = false) private String version;
    @Column(nullable = false) private boolean granted;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;

    protected Consent() {}
    public Consent(UUID userId, Type type, String version, boolean granted) {
        this.id = UUID.randomUUID(); this.userId = userId; this.type = type; this.version = version;
        this.granted = granted; this.recordedAt = Instant.now();
    }
    public Type getType() { return type; }
    public String getVersion() { return version; }
    public boolean isGranted() { return granted; }
    public Instant getRecordedAt() { return recordedAt; }
}
