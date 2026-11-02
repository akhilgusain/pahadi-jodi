package in.pahadijodi.chat.ws;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Authenticates the WebSocket upgrade. Browsers cannot set headers on a WebSocket, so the JWT comes from
 * ?token= (or an Authorization: Bearer header for native clients). Rejects with 401 before upgrading.
 * Operational note: query-string tokens can appear in access logs, so mask "token" in the ingress/gateway logs
 * and keep access tokens short-lived (15 min).
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    static final String USER_ATTR = "pj.userId";
    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);
    private final JwtDecoder decoder;

    public JwtHandshakeInterceptor(JwtDecoder decoder) { this.decoder = decoder; }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
        if (token == null) {
            String header = request.getHeaders().getFirst("Authorization");
            if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) token = header.substring(7).trim();
        }
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            attributes.put(USER_ATTR, decoder.decode(token).getSubject());
            return true;
        } catch (JwtException e) {
            log.debug("WebSocket handshake rejected: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception ex) {}
}
