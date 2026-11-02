package in.pahadijodi.matchmaking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_record")
public class MatchRecord {
    @Id private UUID id;
    @Column(name = "user_a", nullable = false) private UUID userA;
    @Column(name = "user_b", nullable = false) private UUID userB;
    @Column(name = "profile_a", nullable = false) private UUID profileA;
    @Column(name = "profile_b", nullable = false) private UUID profileB;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected MatchRecord() {}

    /** Canonical ordering (smaller user id first) so (A,B) and (B,A) are the same row. */
    public static MatchRecord of(UUID u1, UUID p1, UUID u2, UUID p2) {
        boolean swap = u1.compareTo(u2) > 0;
        var m = new MatchRecord();
        m.id = UUID.randomUUID();
        m.userA = swap ? u2 : u1; m.profileA = swap ? p2 : p1;
        m.userB = swap ? u1 : u2; m.profileB = swap ? p1 : p2;
        m.createdAt = Instant.now();
        return m;
    }
    public UUID getId() { return id; }
    public UUID getUserA() { return userA; }
    public UUID getUserB() { return userB; }
    public UUID getProfileA() { return profileA; }
    public UUID getProfileB() { return profileB; }
    public Instant getCreatedAt() { return createdAt; }
}
