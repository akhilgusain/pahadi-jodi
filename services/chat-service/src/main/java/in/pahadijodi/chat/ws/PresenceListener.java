package in.pahadijodi.chat.ws;

import in.pahadijodi.chat.service.ChatService;
import in.pahadijodi.chat.service.PresenceService;
import java.security.Principal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class PresenceListener {
    private static final Logger log = LoggerFactory.getLogger(PresenceListener.class);
    private final PresenceService presence;
    private final ChatService chat;
    private final ChatDispatcher dispatcher;

    public PresenceListener(PresenceService presence, ChatService chat, ChatDispatcher dispatcher) {
        this.presence = presence;
        this.chat = chat;
        this.dispatcher = dispatcher;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent e) {
        UUID user = userOf(e.getUser());
        if (user == null) return;
        String sessionId = StompHeaderAccessor.wrap(e.getMessage()).getSessionId();
        if (!presence.connected(user, sessionId)) return;
        try {
            chat.deliverPending(user).forEach(dispatcher::delivered);   // messages sent while they were offline
            dispatcher.presence(user, true, chat.peersOf(user));
        } catch (RuntimeException ex) {
            log.warn("post-connect handling failed for {}: {}", user, ex.toString());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent e) {
        UUID user = userOf(e.getUser());
        if (user == null) return;
        if (presence.disconnected(user, e.getSessionId())) {
            try {
                dispatcher.presence(user, false, chat.peersOf(user));
            } catch (RuntimeException ex) {
                log.warn("presence broadcast failed for {}: {}", user, ex.toString());
            }
        }
    }

    private static UUID userOf(Principal p) {
        try { return p == null ? null : UUID.fromString(p.getName()); } catch (IllegalArgumentException ex) { return null; }
    }
}
