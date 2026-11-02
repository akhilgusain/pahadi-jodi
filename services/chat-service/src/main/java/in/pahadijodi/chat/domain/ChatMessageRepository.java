package in.pahadijodi.chat.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findByConversationIdAndSenderIdAndClientMsgId(UUID conversationId, UUID senderId, String clientMsgId);

    @Query("select m from ChatMessage m where m.conversationId = :c order by m.id desc")
    List<ChatMessage> latest(@Param("c") UUID conversationId, Pageable page);

    @Query("select m from ChatMessage m where m.conversationId = :c and m.id < :before order by m.id desc")
    List<ChatMessage> olderThan(@Param("c") UUID conversationId, @Param("before") long before, Pageable page);

    List<ChatMessage> findByRecipientIdAndStatus(UUID recipientId, MessageStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChatMessage m set m.status = :read, m.readAt = :now, m.deliveredAt = coalesce(m.deliveredAt, :now) "
            + "where m.conversationId = :c and m.recipientId = :reader and m.id <= :upTo and m.status <> :read")
    int markRead(@Param("c") UUID conversationId, @Param("reader") UUID reader, @Param("upTo") long upTo,
                 @Param("now") Instant now, @Param("read") MessageStatus read);

    long countByConversationIdAndRecipientIdAndStatusNot(UUID conversationId, UUID recipientId, MessageStatus status);

    @Query("select m.conversationId, count(m) from ChatMessage m where m.recipientId = :u and m.status <> :read group by m.conversationId")
    List<Object[]> unreadGrouped(@Param("u") UUID user, @Param("read") MessageStatus read);

    List<ChatMessage> findByIdIn(Collection<Long> ids);
}
