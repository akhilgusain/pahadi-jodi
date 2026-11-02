package in.pahadijodi.chat.ws;

import in.pahadijodi.chat.config.ChatProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * STOMP over plain WebSocket at /ws (client: @stomp/stompjs, ws://host/ws?token=JWT).
 * Client sends to /app/*; server pushes to /user/queue/events and /user/queue/errors.
 * The simple in-memory broker is single-instance: for several pods switch enableSimpleBroker to a
 * broker relay (RabbitMQ/ActiveMQ STOMP) or a Redis pub/sub fan-out, as designed in docs/03.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtHandshakeInterceptor handshakeInterceptor;
    private final ChatProperties props;
    private final TaskScheduler scheduler;

    public WebSocketConfig(JwtHandshakeInterceptor handshakeInterceptor, ChatProperties props,
                           @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler scheduler) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.props = props;
        this.scheduler = scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(new UserHandshakeHandler())
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(props.ws().allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(scheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(64 * 1024).setSendBufferSizeLimit(512 * 1024);
    }
}
