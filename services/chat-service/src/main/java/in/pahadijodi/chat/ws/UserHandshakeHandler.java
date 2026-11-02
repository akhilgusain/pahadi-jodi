package in.pahadijodi.chat.ws;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/** Makes the JWT subject (account id) the STOMP principal, so convertAndSendToUser(userId, ...) routes correctly. */
class UserHandshakeHandler extends DefaultHandshakeHandler {
    record StompUser(String getName) implements Principal {}

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Object sub = attributes.get(JwtHandshakeInterceptor.USER_ATTR);
        return sub == null ? null : new StompUser(sub.toString());
    }
}
