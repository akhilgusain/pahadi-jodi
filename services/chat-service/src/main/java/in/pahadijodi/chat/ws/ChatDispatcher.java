package in.pahadijodi.chat.ws;

import com.fasterxml.jackson.databind.JsonNode;
import in.pahadijodi.chat.service.Dtos.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes typed events to a user's live sessions on /user/queue/events. Envelope: {"type": "...", "data": {...}}.
 * Types: MESSAGE, MESSAGE_SENT, DELIVERED, READ, TYPING, REACTION, PRESENCE, CALL, CALL_SIGNAL.
 */
@Component
public class ChatDispatcher {
    public record WsEvent(String type, Object data) {}

    private final SimpMessagingTemplate template;

    public ChatDispatcher(SimpMessagingTemplate template) { this.template = template; }

    public void toUser(UUID user, String type, Object data) {
        template.convertAndSendToUser(user.toString(), "/queue/events", new WsEvent(type, data));
    }

    public void message(MessageDto m) {
        toUser(m.recipientId(), "MESSAGE", m);
        toUser(m.senderId(), "MESSAGE_SENT", m);   // ack (resolves clientMsgId -> id) and multi-device sync
    }

    public void delivered(DeliveryReceipt r) {
        toUser(r.senderId(), "DELIVERED", Map.of("conversationId", r.conversationId(), "messageIds", r.messageIds()));
    }

    public void read(ReadReceipt r) {
        toUser(r.peerId(), "READ", Map.of("conversationId", r.conversationId(), "readerId", r.readerId(),
                "upToMessageId", r.upToMessageId()));
    }

    public void typing(UUID conversationId, UUID from, UUID to, boolean typing) {
        toUser(to, "TYPING", Map.of("conversationId", conversationId, "userId", from, "typing", typing));
    }

    public void reaction(ReactionUpdate r) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("messageId", r.messageId());
        data.put("conversationId", r.conversationId());
        data.put("userId", r.userId());
        data.put("emoji", r.emoji());   // null = removed
        toUser(r.userId(), "REACTION", data);
        toUser(r.peerId(), "REACTION", data);
    }

    public void presence(UUID user, boolean online, List<UUID> peers) {
        for (UUID peer : peers) toUser(peer, "PRESENCE", Map.of("userId", user, "online", online));
    }

    /** event: incoming | accepted | declined | cancelled | missed | ended. */
    public void call(UUID to, String event, CallDto call) {
        toUser(to, "CALL", Map.of("event", event, "call", call));
    }

    public void callSignal(UUID to, UUID from, UUID callId, String kind, JsonNode payload) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("callId", callId);
        data.put("from", from);
        data.put("kind", kind);
        data.put("payload", payload);
        toUser(to, "CALL_SIGNAL", data);
    }
}
