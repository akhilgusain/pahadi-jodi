package in.pahadijodi.chat.ws;

import com.fasterxml.jackson.databind.JsonNode;
import in.pahadijodi.chat.domain.MessageType;
import in.pahadijodi.chat.service.CallService;
import in.pahadijodi.chat.service.ChatService;
import in.pahadijodi.chat.service.Dtos.SendCommand;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.security.Principal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * Client -> server STOMP destinations (all under /app):
 *   /app/chat.send    {conversationId, type, body, mediaUrl, mediaDurationSec, clientMsgId}
 *   /app/chat.typing  {conversationId, typing}
 *   /app/chat.read    {conversationId, upToMessageId}
 *   /app/chat.react   {messageId, emoji}              (toggle)
 *   /app/call.signal  {callId, kind: offer|answer|ice|hangup, payload}
 * Failures are pushed to /user/queue/errors as {code, message}.
 */
@Controller
public class ChatWsController {
    private static final Logger log = LoggerFactory.getLogger(ChatWsController.class);

    public record SendPayload(UUID conversationId, MessageType type, String body, String mediaUrl, Integer mediaDurationSec,
                              String clientMsgId) {}
    public record TypingPayload(UUID conversationId, boolean typing) {}
    public record ReadPayload(UUID conversationId, long upToMessageId) {}
    public record ReactPayload(long messageId, String emoji) {}
    public record SignalPayload(UUID callId, String kind, JsonNode payload) {}
    public record ErrorEvent(String code, String message) {}

    private final ChatService chat;
    private final CallService calls;
    private final ChatDispatcher dispatcher;

    public ChatWsController(ChatService chat, CallService calls, ChatDispatcher dispatcher) {
        this.chat = chat;
        this.calls = calls;
        this.dispatcher = dispatcher;
    }

    @MessageMapping("/chat.send")
    public void send(Principal p, @Payload SendPayload m) {
        var msg = chat.send(uid(p), new SendCommand(m.conversationId(), m.type(), m.body(), m.mediaUrl(), m.mediaDurationSec(), m.clientMsgId()));
        dispatcher.message(msg);
    }

    @MessageMapping("/chat.typing")
    public void typing(Principal p, @Payload TypingPayload t) {
        UUID me = uid(p);
        dispatcher.typing(t.conversationId(), me, chat.peerInConversation(me, t.conversationId()), t.typing());
    }

    @MessageMapping("/chat.read")
    public void read(Principal p, @Payload ReadPayload r) {
        chat.markRead(uid(p), r.conversationId(), r.upToMessageId()).ifPresent(dispatcher::read);
    }

    @MessageMapping("/chat.react")
    public void react(Principal p, @Payload ReactPayload r) {
        dispatcher.reaction(chat.react(uid(p), r.messageId(), r.emoji()));
    }

    @MessageMapping("/call.signal")
    public void signal(Principal p, @Payload SignalPayload s) {
        UUID me = uid(p);
        var res = calls.signal(me, s.callId(), s.kind(), s.payload());
        if (res.ended()) {
            dispatcher.call(res.target(), "ended", res.call());
            dispatcher.call(me, "ended", res.call());
        } else {
            dispatcher.callSignal(res.target(), me, s.callId(), s.kind().toLowerCase(), s.payload());
        }
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public ErrorEvent onError(Exception e) {
        if (e instanceof PjException pe) return new ErrorEvent(pe.code(), pe.getMessage());
        log.warn("STOMP handler error", e);
        return new ErrorEvent("INTERNAL_ERROR", "Something went wrong");
    }

    private static UUID uid(Principal p) { return UUID.fromString(p.getName()); }
}
