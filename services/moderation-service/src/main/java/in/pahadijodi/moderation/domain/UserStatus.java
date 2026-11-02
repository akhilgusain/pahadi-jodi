package in.pahadijodi.moderation.domain;

import in.pahadijodi.moderation.domain.Enums.AccountStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_status")
public class UserStatus {
    @Id @Column(name = "user_id") private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AccountStatus status;
    private Instant until;
    @Column(nullable = false) private String source;   // STRIKES | MANUAL
    private String reason;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected UserStatus() {}
    public UserStatus(UUID userId) {
        this.userId = userId;
        this.status = AccountStatus.ACTIVE;
        this.source = "STRIKES";
        this.updatedAt = Instant.now();
    }
    public void set(AccountStatus s, Instant until, String source, String reason) {
        this.status = s;
        this.until = until;
        this.source = source;
        this.reason = reason;
        this.updatedAt = Instant.now();
    }
    /** A temporary suspension that has run out counts as ACTIVE. */
    public AccountStatus effective() {
        if (status == AccountStatus.SUSPENDED && until != null && until.isBefore(Instant.now())) return AccountStatus.ACTIVE;
        return status;
    }
    public UUID getUserId() { return userId; }
    public AccountStatus getStatus() { return status; }
    public Instant getUntil() { return until; }
    public String getSource() { return source; }
    public String getReason() { return reason; }
}
