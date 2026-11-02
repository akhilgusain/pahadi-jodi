package in.pahadijodi.chat.port;

import in.pahadijodi.chat.domain.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for chat messages. The local adapter is JPA (Postgres/H2); the production adapter
 * for 1M+ users is DynamoDB (PK = conversationId, SK = time-ordered message id) per docs/03.
 */
public interface MessageStore {
    ChatMessage save(ChatMessage message);
    Optional<ChatMessage> findById(long id);
    Optional<ChatMessage> findByClientMsgId(UUID conversationId, UUID senderId, String clientMsgId);
    /** Newest first. {@code beforeId == null} means from the latest message. */
    List<ChatMessage> history(UUID conversationId, Long beforeId, int limit);
    /** Messages addressed to the user that were never delivered (status SENT). Managed entities. */
    List<ChatMessage> undelivered(UUID recipientId);
    /** Marks messages addressed to {@code reader} up to {@code upToId} as READ; returns rows changed. */
    int markRead(UUID conversationId, UUID reader, long upToId, Instant now);
    long unread(UUID conversationId, UUID user);
    Map<UUID, Long> unreadByConversation(UUID user);
}
