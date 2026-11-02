package in.pahadijodi.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.chat.service.ChatService;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** End-to-end over a real socket: JWT handshake, STOMP events, offline delivery, presence, call signalling. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestJwt.Config.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chatws;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.kafka.consumers.enabled=false", "pj.outbox.relay.enabled=false"})
class ChatWebSocketTest {
    @LocalServerPort int port;
    @Autowired ChatService chat;
    @Autowired TestRestTemplate rest;

    /** A connected STOMP client that queues every event pushed to /user/queue/events and /user/queue/errors. */
    class Client implements AutoCloseable {
        final UUID user;
        final StompSession session;
        final BlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>();
        final BlockingQueue<Map<String, Object>> errors = new LinkedBlockingQueue<>();

        Client(UUID user, String token) throws Exception {
            this.user = user;
            var stomp = new WebSocketStompClient(new StandardWebSocketClient());
            stomp.setMessageConverter(new MappingJackson2MessageConverter());
            session = stomp.connectAsync("ws://localhost:" + port + "/ws?token=" + token, new WebSocketHttpHeaders(),
                    new StompHeaders(), new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
            subscribe("/user/queue/events", events);
            subscribe("/user/queue/errors", errors);
            Thread.sleep(300);   // let the subscriptions register on the broker
        }

        @SuppressWarnings("unchecked")
        private void subscribe(String dest, BlockingQueue<Map<String, Object>> into) {
            session.subscribe(dest, new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders h) { return Map.class; }
                @Override public void handleFrame(StompHeaders h, Object payload) { into.add((Map<String, Object>) payload); }
            });
        }

        void send(String dest, Map<String, Object> payload) {
            var h = new StompHeaders();
            h.setDestination(dest);
            h.setContentType(MimeTypeUtilsHolder.JSON);
            session.send(h, payload);
        }

        /** Next event of the given type (earlier events of other types are skipped). */
        @SuppressWarnings("unchecked")
        Map<String, Object> await(String type) throws Exception {
            long end = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < end) {
                var e = events.poll(200, TimeUnit.MILLISECONDS);
                if (e != null && type.equals(e.get("type"))) return (Map<String, Object>) e.get("data");
            }
            throw new AssertionError("No " + type + " event for " + user);
        }

        @Override public void close() { if (session.isConnected()) session.disconnect(); }
    }

    static class MimeTypeUtilsHolder { static final org.springframework.util.MimeType JSON = MediaType.APPLICATION_JSON; }

    private Client connect(UUID u) throws Exception { return new Client(u, TestJwt.token(u)); }

    private Map<String, Object> restPost(UUID as, String path, Map<String, Object> body) {
        var h = new HttpHeaders();
        h.setBearerAuth(TestJwt.token(as));
        h.setContentType(MediaType.APPLICATION_JSON);
        var res = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(res.getStatusCode().is2xxSuccessful()).as(path + " -> " + res.getStatusCode() + " " + res.getBody()).isTrue();
        return res.getBody();
    }

    @Test
    void handshakeRequiresValidJwt() {
        var stomp = new WebSocketStompClient(new StandardWebSocketClient());
        assertThatThrownBy(() -> stomp.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                new StompHeaders(), new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> stomp.connectAsync("ws://localhost:" + port + "/ws?token=garbage", new WebSocketHttpHeaders(),
                new StompHeaders(), new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> stomp.connectAsync("ws://localhost:" + port + "/ws?token=" + TestJwt.token(UUID.randomUUID(), -60),
                new WebSocketHttpHeaders(), new StompHeaders(), new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS)).isInstanceOf(Exception.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void realtimeMessagingPresenceReceiptsReactionsAndOfflineDelivery() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), d = UUID.randomUUID();
        chat.recordMatch(a, b, "ws-1");
        chat.recordMatch(a, d, "ws-2");
        UUID convAB = chat.ensureConversation(a, b).getId();
        UUID convAD = chat.ensureConversation(a, d).getId();

        try (Client ca = connect(a)) {
            Client cb = connect(b);
            assertThat(ca.await("PRESENCE")).containsEntry("userId", b.toString()).containsEntry("online", true);

            // A -> B: B gets MESSAGE already DELIVERED (online), A gets the MESSAGE_SENT ack with its clientMsgId
            ca.send("/app/chat.send", Map.of("conversationId", convAB.toString(), "type", "TEXT", "body", "Namaste Bhaiji", "clientMsgId", "w-1"));
            var got = cb.await("MESSAGE");
            assertThat(got).containsEntry("body", "Namaste Bhaiji").containsEntry("status", "DELIVERED");
            var ack = ca.await("MESSAGE_SENT");
            assertThat(ack).containsEntry("clientMsgId", "w-1");
            long msgId = ((Number) got.get("id")).longValue();

            // typing indicator, read receipt, reaction
            cb.send("/app/chat.typing", Map.of("conversationId", convAB.toString(), "typing", true));
            assertThat(ca.await("TYPING")).containsEntry("typing", true).containsEntry("userId", b.toString());
            cb.send("/app/chat.read", Map.of("conversationId", convAB.toString(), "upToMessageId", msgId));
            assertThat(ca.await("READ")).containsEntry("readerId", b.toString());
            cb.send("/app/chat.react", Map.of("messageId", msgId, "emoji", "❤️"));
            assertThat(ca.await("REACTION")).containsEntry("emoji", "❤️");
            assertThat(cb.await("REACTION")).containsEntry("messageId", (int) msgId);

            // errors come back on /user/queue/errors
            try (Client outsider = connect(UUID.randomUUID())) {
                outsider.send("/app/chat.send", Map.of("conversationId", convAB.toString(), "body", "hi"));
                var err = outsider.errors.poll(5, TimeUnit.SECONDS);
                assertThat(err).isNotNull().containsEntry("code", "CONVERSATION_NOT_FOUND");
            }
            ca.send("/app/chat.send", Map.of("conversationId", convAB.toString(), "body", "you idiot"));
            assertThat(ca.errors.poll(5, TimeUnit.SECONDS)).containsEntry("code", "MESSAGE_BLOCKED");

            // offline delivery: D is offline when A sends (status SENT); on connect A is told DELIVERED
            ca.send("/app/chat.send", Map.of("conversationId", convAD.toString(), "body", "Kya haal hai?", "clientMsgId", "w-2"));
            assertThat(ca.await("MESSAGE_SENT")).containsEntry("status", "SENT");
            Client cd = connect(d);
            var delivered = ca.await("DELIVERED");
            assertThat(delivered).containsEntry("conversationId", convAD.toString());
            assertThat((List<Object>) delivered.get("messageIds")).hasSize(1);
            assertThat(ca.await("PRESENCE")).containsEntry("userId", d.toString());   // D came online
            cd.close();

            // call lifecycle (REST) + signalling (STOMP)
            var call = restPost(a, "/api/v1/chat/calls", Map.of("conversationId", convAB.toString(), "mode", "VIDEO", "recordingConsent", true));
            String callId = (String) call.get("id");
            assertThat(cb.await("CALL")).containsEntry("event", "incoming");
            restPost(b, "/api/v1/chat/calls/" + callId + "/accept", Map.of("recordingConsent", true));
            var accepted = ca.await("CALL");
            assertThat(accepted).containsEntry("event", "accepted");
            assertThat((Map<String, Object>) accepted.get("call")).containsEntry("recordingAllowed", true);

            ca.send("/app/call.signal", Map.of("callId", callId, "kind", "offer", "payload", Map.of("sdp", "v=0 offer")));
            var offer = cb.await("CALL_SIGNAL");
            assertThat(offer).containsEntry("kind", "offer").containsEntry("from", a.toString());
            assertThat((Map<String, Object>) offer.get("payload")).containsEntry("sdp", "v=0 offer");
            cb.send("/app/call.signal", Map.of("callId", callId, "kind", "answer", "payload", Map.of("sdp", "v=0 answer")));
            assertThat(ca.await("CALL_SIGNAL")).containsEntry("kind", "answer");
            ca.send("/app/call.signal", Map.of("callId", callId, "kind", "answer", "payload", Map.of("sdp", "bad")));   // caller may not answer
            assertThat(ca.errors.poll(5, TimeUnit.SECONDS)).containsEntry("code", "INVALID_SIGNAL");
            ca.send("/app/call.signal", Map.of("callId", callId, "kind", "hangup"));
            assertThat(cb.await("CALL")).containsEntry("event", "ended");

            // B disconnects -> A sees B offline
            cb.close();
            assertThat(ca.await("PRESENCE")).containsEntry("userId", b.toString()).containsEntry("online", false);
        }
    }
}
