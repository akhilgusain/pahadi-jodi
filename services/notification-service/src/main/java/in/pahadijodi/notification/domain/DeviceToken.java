package in.pahadijodi.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_token")
public class DeviceToken {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, unique = true) private String token;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Platform platform;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;

    protected DeviceToken() {}
    public DeviceToken(UUID userId, String token, Platform platform, Instant now) {
        this.id = UUID.randomUUID(); this.userId = userId; this.token = token; this.platform = platform;
        this.createdAt = now; this.lastSeenAt = now;
    }
    /** A token can move to another account (shared phone / re-login). */
    public void claim(UUID userId, Platform platform, Instant now) { this.userId = userId; this.platform = platform; this.lastSeenAt = now; }

    public UUID getUserId() { return userId; }
    public String getToken() { return token; }
    public Platform getPlatform() { return platform; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
