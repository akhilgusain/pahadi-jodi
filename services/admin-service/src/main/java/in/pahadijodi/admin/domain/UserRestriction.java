package in.pahadijodi.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** The ban currently (or previously) imposed on a user; kept so unban policy knows how serious the ban was. */
@Entity
@Table(name = "user_restriction")
public class UserRestriction {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private boolean permanent;
    @Column(name = "banned_until") private Instant bannedUntil;
    @Column(name = "duration_days") private Integer durationDays;
    private String reason;
    @Column(name = "imposed_by") private String imposedBy;
    @Column(name = "imposed_at", nullable = false) private Instant imposedAt;

    protected UserRestriction() {}

    public UserRestriction(UUID userId) { this.userId = userId; }

    public void impose(boolean permanent, Integer days, String reason, String by) {
        this.active = true; this.permanent = permanent; this.durationDays = permanent ? null : days;
        this.bannedUntil = permanent ? null : Instant.now().plusSeconds(days * 86400L);
        this.reason = reason; this.imposedBy = by; this.imposedAt = Instant.now();
    }
    public void lift() { this.active = false; }

    /** Active and not yet expired. */
    public boolean isInForce() {
        return active && (permanent || (bannedUntil != null && bannedUntil.isAfter(Instant.now())));
    }
    public UUID getUserId() { return userId; }
    public boolean isPermanent() { return permanent; }
    public Instant getBannedUntil() { return bannedUntil; }
    public Integer getDurationDays() { return durationDays; }
    public String getReason() { return reason; }
}
