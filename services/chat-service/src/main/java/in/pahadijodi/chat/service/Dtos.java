package in.pahadijodi.chat.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.pahadijodi.chat.domain.CallMode;
import in.pahadijodi.chat.domain.CallState;
import in.pahadijodi.chat.domain.MessageStatus;
import in.pahadijodi.chat.domain.MessageType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Dtos {
    private Dtos() {}

    public record ReactionDto(UUID userId, String emoji) {}

    public record MessageDto(Long id, UUID conversationId, UUID senderId, UUID recipientId, MessageType type, String body,
                             String mediaUrl, Integer mediaDurationSec, String clientMsgId, MessageStatus status,
                             boolean flagged, Instant createdAt, Instant deliveredAt, Instant readAt,
                             List<ReactionDto> reactions) {}

    public record ConversationDto(UUID id, UUID peerId, boolean peerOnline, long unread, MessageDto lastMessage,
                                  Instant lastMessageAt) {}

    public record SendCommand(UUID conversationId, MessageType type, String body, String mediaUrl,
                              Integer mediaDurationSec, String clientMsgId) {}

    /** Newest-first page; pass {@code nextBefore} as {@code before} to get older messages (null = no more). */
    public record HistoryPage(List<MessageDto> items, Long nextBefore) {}

    public record ReadReceipt(UUID conversationId, UUID readerId, UUID peerId, long upToMessageId, int count) {}

    public record DeliveryReceipt(UUID conversationId, UUID senderId, List<Long> messageIds) {}

    /** {@code emoji == null} means the reaction was removed. */
    public record ReactionUpdate(Long messageId, UUID conversationId, UUID userId, String emoji, UUID peerId) {}

    public record TranslationDto(Long messageId, String language, String text) {}

    public record CallDto(UUID id, UUID conversationId, UUID callerId, UUID calleeId, CallMode mode, CallState state,
                          boolean recordingAllowed, Instant createdAt, Instant answeredAt, Instant endedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IceServer(List<String> urls, String username, String credential) {}
}
