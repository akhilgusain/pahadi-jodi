package in.pahadijodi.chat.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_message")
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "conversation_id", nullable = false) private UUID conversationId;
    @Column(name = "sender_id", nullable = false) private UUID senderId;
    @Column(name = "recipient_id", nullable = false) private UUID recipientId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MessageType type;
    private String body;
    @Column(name = "media_url") private String mediaUrl;
    @Column(name = "media_duration_sec") private Integer mediaDurationSec;
    @Column(name = "client_msg_id", nullable = false) private String clientMsgId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MessageStatus status;
    @Column(nullable = false) private boolean flagged;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "read_at") private Instant readAt;

    protected ChatMessage() {}

    public ChatMessage(UUID conversationId, UUID senderId, UUID recipientId, MessageType type, String body,
                       String mediaUrl, Integer mediaDurationSec, String clientMsgId, boolean flagged) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.type = type;
        this.body = body;
        this.mediaUrl = mediaUrl;
        this.mediaDurationSec = mediaDurationSec;
        this.clientMsgId = clientMsgId;
        this.flagged = flagged;
        this.status = MessageStatus.SENT;
        this.createdAt = Instant.now();
    }

    public void markDelivered(Instant at) {
        if (status == MessageStatus.SENT) { status = MessageStatus.DELIVERED; deliveredAt = at; }
    }

    public Long getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public UUID getSenderId() { return senderId; }
    public UUID getRecipientId() { return recipientId; }
    public MessageType getType() { return type; }
    public String getBody() { return body; }
    public String getMediaUrl() { return mediaUrl; }
    public Integer getMediaDurationSec() { return mediaDurationSec; }
    public String getClientMsgId() { return clientMsgId; }
    public MessageStatus getStatus() { return status; }
    public boolean isFlagged() { return flagged; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getReadAt() { return readAt; }
}
