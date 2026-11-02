package in.pahadijodi.chat.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Local projection of pj.match.created: the pair of users allowed to chat. */
@Entity
@Table(name = "chat_match")
public class ChatMatch {
    @Id private UUID id;
    @Column(name = "user_a", nullable = false) private UUID userA;
    @Column(name = "user_b", nullable = false) private UUID userB;
    @Column(name = "match_ref") private String matchRef;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ChatMatch() {}
    public ChatMatch(UUID x, UUID y, String matchRef) {
        this.id = UUID.randomUUID();
        if (Conversation.canonicalFirst(x, y)) { userA = x; userB = y; } else { userA = y; userB = x; }
        this.matchRef = matchRef;
        this.createdAt = Instant.now();
    }
    public UUID getUserA() { return userA; }
    public UUID getUserB() { return userB; }
}
