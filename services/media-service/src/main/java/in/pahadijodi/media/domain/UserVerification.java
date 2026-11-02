package in.pahadijodi.media.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_verification")
public class UserVerification {
    public enum Level { NONE, ID_VERIFIED, BLUE_TICK }

    @Id @Column(name = "user_id") private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Level level;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected UserVerification() {}
    public UserVerification(UUID userId, Level level) {
        this.userId = userId;
        this.level = level;
        this.updatedAt = Instant.now();
    }
    public void setLevel(Level l) { this.level = l; this.updatedAt = Instant.now(); }
    public UUID getUserId() { return userId; }
    public Level getLevel() { return level; }
}
