package in.pahadijodi.chat.adapter;

import in.pahadijodi.chat.domain.ChatMessage;
import in.pahadijodi.chat.domain.ChatMessageRepository;
import in.pahadijodi.chat.domain.MessageStatus;
import in.pahadijodi.chat.port.MessageStore;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class JpaMessageStore implements MessageStore {
    private final ChatMessageRepository repo;

    public JpaMessageStore(ChatMessageRepository repo) { this.repo = repo; }

    @Override public ChatMessage save(ChatMessage m) { return repo.save(m); }
    @Override public Optional<ChatMessage> findById(long id) { return repo.findById(id); }

    @Override
    public Optional<ChatMessage> findByClientMsgId(UUID conversationId, UUID senderId, String clientMsgId) {
        return repo.findByConversationIdAndSenderIdAndClientMsgId(conversationId, senderId, clientMsgId);
    }

    @Override
    public List<ChatMessage> history(UUID conversationId, Long beforeId, int limit) {
        var page = PageRequest.of(0, limit);
        return beforeId == null ? repo.latest(conversationId, page) : repo.olderThan(conversationId, beforeId, page);
    }

    @Override public List<ChatMessage> undelivered(UUID recipientId) {
        return repo.findByRecipientIdAndStatus(recipientId, MessageStatus.SENT);
    }

    @Override
    public int markRead(UUID conversationId, UUID reader, long upToId, Instant now) {
        return repo.markRead(conversationId, reader, upToId, now, MessageStatus.READ);
    }

    @Override
    public long unread(UUID conversationId, UUID user) {
        return repo.countByConversationIdAndRecipientIdAndStatusNot(conversationId, user, MessageStatus.READ);
    }

    @Override
    public Map<UUID, Long> unreadByConversation(UUID user) {
        Map<UUID, Long> out = new HashMap<>();
        for (Object[] row : repo.unreadGrouped(user, MessageStatus.READ)) out.put((UUID) row[0], (Long) row[1]);
        return out;
    }
}
