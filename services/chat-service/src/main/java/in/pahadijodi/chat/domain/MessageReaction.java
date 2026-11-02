package in.pahadijodi.chat.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_reaction")
public class MessageReaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "message_id", nullable = false) private Long messageId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private String emoji;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected MessageReaction() {}
    public MessageReaction(Long messageId, UUID userId, String emoji) {
        this.messageId = messageId; this.userId = userId; this.emoji = emoji; this.createdAt = Instant.now();
    }
    public void changeTo(String emoji) { this.emoji = emoji; }
    public Long getMessageId() { return messageId; }
    public UUID getUserId() { return userId; }
    public String getEmoji() { return emoji; }
}
