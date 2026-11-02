package in.pahadijodi.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device")
public class Device {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String platform;
    @Column(name = "last_seen", nullable = false) private Instant lastSeen;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "revoked_at") private Instant revokedAt;

    protected Device() {}
    public Device(UUID userId, String name, String platform) {
        this.id = UUID.randomUUID(); this.userId = userId; this.name = name; this.platform = platform;
        this.createdAt = this.lastSeen = Instant.now();
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getPlatform() { return platform; }
    public Instant getLastSeen() { return lastSeen; }
    public boolean isRevoked() { return revokedAt != null; }
    public void touch() { this.lastSeen = Instant.now(); }
    public void revoke() { if (revokedAt == null) revokedAt = Instant.now(); }
}
