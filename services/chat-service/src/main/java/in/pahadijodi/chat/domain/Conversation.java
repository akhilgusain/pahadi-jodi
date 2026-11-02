package in.pahadijodi.chat.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A 1:1 conversation. user_a/user_b are stored in canonical (string) order so each pair has one row. */
@Entity
@Table(name = "conversation")
public class Conversation {
    @Id private UUID id;
    @Column(name = "user_a", nullable = false) private UUID userA;
    @Column(name = "user_b", nullable = false) private UUID userB;
    @Column(name = "last_message_id") private Long lastMessageId;
    @Column(name = "last_message_at") private Instant lastMessageAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Conversation() {}

    public Conversation(UUID x, UUID y) {
        this.id = UUID.randomUUID();
        if (canonicalFirst(x, y)) { this.userA = x; this.userB = y; } else { this.userA = y; this.userB = x; }
        this.createdAt = Instant.now();
    }

    /** Canonical order used everywhere a pair is stored (string compare == Postgres uuid byte order). */
    public static boolean canonicalFirst(UUID x, UUID y) {
        return x.toString().compareTo(y.toString()) < 0;
    }

    public boolean hasMember(UUID user) { return userA.equals(user) || userB.equals(user); }
    public UUID peerOf(UUID user) { return userA.equals(user) ? userB : userA; }
    public void touch(long messageId, Instant at) { this.lastMessageId = messageId; this.lastMessageAt = at; }

    public UUID getId() { return id; }
    public UUID getUserA() { return userA; }
    public UUID getUserB() { return userB; }
    public Long getLastMessageId() { return lastMessageId; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getCreatedAt() { return createdAt; }
}
