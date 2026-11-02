package in.pahadijodi.media.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_link")
public class MatchLink {
    @Id private UUID id;
    @Column(name = "user_a", nullable = false) private UUID userA;
    @Column(name = "user_b", nullable = false) private UUID userB;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected MatchLink() {}
    public MatchLink(UUID a, UUID b) {
        this.id = UUID.randomUUID();
        this.userA = a;
        this.userB = b;
        this.createdAt = Instant.now();
    }
    public UUID getUserA() { return userA; }
    public UUID getUserB() { return userB; }
}
