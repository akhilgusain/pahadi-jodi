package in.pahadijodi.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.chat.domain.CallSession;
import in.pahadijodi.chat.domain.CallSessionRepository;
import in.pahadijodi.chat.domain.CallState;
import in.pahadijodi.chat.events.MatchEventsConsumer;
import in.pahadijodi.chat.service.CallService;
import in.pahadijodi.chat.service.ChatService;
import in.pahadijodi.common.outbox.OutboxRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwt.Config.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chattest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.kafka.consumers.enabled=false", "pj.outbox.relay.enabled=false",
        "pj.chat.rtc.turn-urls=turn:turn.test:3478", "pj.chat.rtc.turn-secret=s3cret"})
class ChatFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ChatService chat;
    @Autowired CallService calls;
    @Autowired CallSessionRepository callRepo;
    @Autowired OutboxRepository outbox;
    @Autowired MatchEventsConsumer matchConsumer;
    @Autowired JdbcTemplate jdbc;

    static JwtRequestPostProcessor as(UUID user) { return jwt().jwt(j -> j.subject(user.toString())); }

    private JsonNode body(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }

    private ResultActions post(UUID as, String url, String content) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).with(as(as))
                .contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private UUID matchedConversation(UUID a, UUID b) throws Exception {
        chat.recordMatch(a, b, "m-" + UUID.randomUUID());
        return UUID.fromString(body(post(a, "/api/v1/chat/conversations", "{\"peerId\":\"" + b + "\"}").andExpect(status().isOk())).get("id").asText());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/chat/conversations")).andExpect(status().isUnauthorized());
    }

    @Test
    void unmatchedUsersCannotChat() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        post(a, "/api/v1/chat/conversations", "{\"peerId\":\"" + b + "\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_MATCHED"));
        post(a, "/api/v1/chat/conversations", "{\"peerId\":\"" + a + "\"}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sendHistoryUnreadReadAndReactions() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        UUID conv = matchedConversation(a, b);

        JsonNode sent = body(post(a, "/api/v1/chat/conversations/" + conv + "/messages",
                "{\"type\":\"TEXT\",\"body\":\"Namaste, kaise hain aap?\",\"clientMsgId\":\"c-1\"}").andExpect(status().isCreated()));
        long id = sent.get("id").asLong();
        assertThat(sent.get("status").asText()).isEqualTo("SENT");   // recipient offline

        // idempotent retry with the same clientMsgId returns the same message
        JsonNode again = body(post(a, "/api/v1/chat/conversations/" + conv + "/messages",
                "{\"type\":\"TEXT\",\"body\":\"Namaste, kaise hain aap?\",\"clientMsgId\":\"c-1\"}").andExpect(status().isCreated()));
        assertThat(again.get("id").asLong()).isEqualTo(id);

        // B sees 1 unread + last message preview
        mvc.perform(get("/api/v1/chat/unread-count").with(as(b))).andExpect(jsonPath("$.unread").value(1));
        mvc.perform(get("/api/v1/chat/conversations").with(as(b)))
                .andExpect(jsonPath("$[0].unread").value(1))
                .andExpect(jsonPath("$[0].peerId").value(a.toString()))
                .andExpect(jsonPath("$[0].lastMessage.body").value("Namaste, kaise hain aap?"));

        // the event carries ids only, never the text
        var events = outbox.findAll().stream().filter(e -> e.getTopic().equals("pj.chat.message-sent.v1")).filter(e -> {
            try {
                var p = json.readTree(e.getEnvelope()).path("payload");
                return p.path("messageId").asText().equals(String.valueOf(id)) && p.path("conversationId").asText().equals(conv.toString());
            } catch (Exception ex) { return false; }
        }).toList();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEnvelope()).contains("recipientUserId").doesNotContain("kaise hain");

        // read receipt
        post(b, "/api/v1/chat/conversations/" + conv + "/read", "{\"upToMessageId\":" + id + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1));
        post(b, "/api/v1/chat/conversations/" + conv + "/read", "{\"upToMessageId\":" + id + "}")
                .andExpect(jsonPath("$.updated").value(0));
        mvc.perform(get("/api/v1/chat/unread-count").with(as(b))).andExpect(jsonPath("$.unread").value(0));

        // reaction toggle: add, replace, remove
        mvc.perform(put("/api/v1/chat/messages/" + id + "/reaction").with(as(b)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"🙏\"}")).andExpect(jsonPath("$.emoji").value("🙏"));
        mvc.perform(get("/api/v1/chat/conversations/" + conv + "/messages").with(as(a)))
                .andExpect(jsonPath("$.items[0].status").value("READ"))
                .andExpect(jsonPath("$.items[0].reactions[0].emoji").value("🙏"));
        mvc.perform(put("/api/v1/chat/messages/" + id + "/reaction").with(as(b)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"🙏\"}")).andExpect(jsonPath("$.emoji").doesNotExist());
        mvc.perform(get("/api/v1/chat/conversations/" + conv + "/messages").with(as(a)))
                .andExpect(jsonPath("$.items[0].reactions").isEmpty());
    }

    @Test
    void historyIsPaginatedNewestFirst() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        UUID conv = matchedConversation(a, b);
        for (int i = 1; i <= 5; i++) {
            post(a, "/api/v1/chat/conversations/" + conv + "/messages", "{\"body\":\"msg " + i + "\"}").andExpect(status().isCreated());
        }
        JsonNode p1 = body(mvc.perform(get("/api/v1/chat/conversations/" + conv + "/messages?limit=2").with(as(b))));
        assertThat(p1.get("items")).hasSize(2);
        assertThat(p1.get("items").get(0).get("body").asText()).isEqualTo("msg 5");
        long cursor = p1.get("nextBefore").asLong();
        JsonNode p2 = body(mvc.perform(get("/api/v1/chat/conversations/" + conv + "/messages?limit=2&before=" + cursor).with(as(b))));
        assertThat(p2.get("items").get(0).get("body").asText()).isEqualTo("msg 3");
        JsonNode p3 = body(mvc.perform(get("/api/v1/chat/conversations/" + conv + "/messages?limit=10&before=" + p2.get("nextBefore").asLong()).with(as(b))));
        assertThat(p3.get("items")).hasSize(1);
        assertThat(p3.get("nextBefore").isNull()).isTrue();
    }

    @Test
    void outsiderGetsNotFound() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), outsider = UUID.randomUUID();
        UUID conv = matchedConversation(a, b);
        long id = body(post(a, "/api/v1/chat/conversations/" + conv + "/messages", "{\"body\":\"hello\"}")).get("id").asLong();
        mvc.perform(get("/api/v1/chat/conversations/" + conv + "/messages").with(as(outsider))).andExpect(status().isNotFound());
        post(outsider, "/api/v1/chat/conversations/" + conv + "/messages", "{\"body\":\"hi\"}").andExpect(status().isNotFound());
        post(outsider, "/api/v1/chat/messages/" + id + "/translate?to=hi", "{}").andExpect(status().isNotFound());
    }

    @Test
    void moderationBlocksAbuseAndFlagsContactSharing() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        UUID conv = matchedConversation(a, b);
        post(a, "/api/v1/chat/conversations/" + conv + "/messages", "{\"body\":\"you stupid person\"}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("MESSAGE_BLOCKED"));
        post(a, "/api/v1/chat/conversations/" + conv + "/messages", "{\"body\":\"call me on 98765 43210 now\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.flagged").value(true));
        post(a, "/api/v1/chat/conversations/" + conv + "/messages", "{\"body\":\"nice to meet you\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.flagged").value(false));
    }

    @Test
    void mediaAndValidationRules() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        String url = "/api/v1/chat/conversations/" + matchedConversation(a, b) + "/messages";
        post(a, url, "{\"type\":\"TEXT\",\"body\":\"  \"}").andExpect(status().isUnprocessableEntity());
        post(a, url, "{\"type\":\"IMAGE\"}").andExpect(status().isUnprocessableEntity());
        post(a, url, "{\"type\":\"VOICE_NOTE\",\"mediaUrl\":\"https://cdn.example/v.m4a\",\"mediaDurationSec\":900}").andExpect(status().isUnprocessableEntity());
        post(a, url, "{\"type\":\"VOICE_NOTE\",\"mediaUrl\":\"https://cdn.example/v.m4a\",\"mediaDurationSec\":42}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("VOICE_NOTE"));
        post(a, url, "{\"type\":\"VIDEO\",\"mediaUrl\":\"javascript:alert(1)\"}").andExpect(status().isUnprocessableEntity());
        post(a, url, "{\"type\":\"IMAGE\",\"mediaUrl\":\"https://cdn.example/p.jpg\",\"body\":\"our village\"}").andExpect(status().isCreated());
    }

    @Test
    void translation() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        UUID conv = matchedConversation(a, b);
        long id = body(post(a, "/api/v1/chat/conversations/" + conv + "/messages", "{\"body\":\"Shubh ratri\"}")).get("id").asLong();
        post(b, "/api/v1/chat/messages/" + id + "/translate?to=hi", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("hi")).andExpect(jsonPath("$.text").value("[hi] Shubh ratri"));
        post(b, "/api/v1/chat/messages/" + id + "/translate?to=fr", "").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void callConsentStateMachine() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        UUID conv = matchedConversation(a, b);
        String start = "{\"conversationId\":\"" + conv + "\",\"mode\":\"VIDEO\",\"recordingConsent\":true}";

        String callId = body(post(a, "/api/v1/chat/calls", start).andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("REQUESTED"))).get("id").asText();
        post(b, "/api/v1/chat/calls", start).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CALL_IN_PROGRESS"));
        post(a, "/api/v1/chat/calls/" + callId + "/accept", "{}").andExpect(status().isForbidden());                // caller cannot accept
        post(b, "/api/v1/chat/calls/" + callId + "/accept", "{\"recordingConsent\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACCEPTED")).andExpect(jsonPath("$.recordingAllowed").value(true));
        post(b, "/api/v1/chat/calls/" + callId + "/decline", "").andExpect(status().isConflict());                  // too late
        post(a, "/api/v1/chat/calls/" + callId + "/end", "").andExpect(jsonPath("$.state").value("ENDED"));

        // callee consents to the call but not to recording -> recording not allowed
        String c2 = body(post(a, "/api/v1/chat/calls", start)).get("id").asText();
        post(b, "/api/v1/chat/calls/" + c2 + "/accept", "{\"recordingConsent\":false}").andExpect(jsonPath("$.recordingAllowed").value(false));
        post(b, "/api/v1/chat/calls/" + c2 + "/end", "").andExpect(jsonPath("$.state").value("ENDED"));

        String c3 = body(post(a, "/api/v1/chat/calls", start)).get("id").asText();
        post(b, "/api/v1/chat/calls/" + c3 + "/decline", "").andExpect(jsonPath("$.state").value("DECLINED"));

        String c4 = body(post(a, "/api/v1/chat/calls", start)).get("id").asText();
        post(a, "/api/v1/chat/calls/" + c4 + "/end", "").andExpect(jsonPath("$.state").value("CANCELLED"));

        // outsiders cannot see or touch the call
        mvc.perform(get("/api/v1/chat/calls/" + c4).with(as(UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void unansweredCallBecomesMissed() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        UUID conv = matchedConversation(a, b);
        UUID callId = UUID.fromString(body(post(a, "/api/v1/chat/calls",
                "{\"conversationId\":\"" + conv + "\",\"mode\":\"AUDIO\"}")).get("id").asText());
        assertThat(calls.expireRinging()).noneMatch(c -> c.id().equals(callId));    // still ringing
        jdbc.update("update call_session set created_at = dateadd('MINUTE', -5, current_timestamp) where id = ?", callId);
        assertThat(calls.expireRinging()).anyMatch(c -> c.id().equals(callId) && c.state() == CallState.MISSED);
        CallSession c = callRepo.findById(callId).orElseThrow();
        assertThat(c.getState()).isEqualTo(CallState.MISSED);
    }

    @Test
    void iceServersUseEphemeralTurnCredentials() throws Exception {
        UUID a = UUID.randomUUID();
        JsonNode res = body(mvc.perform(get("/api/v1/chat/calls/ice-servers").with(as(a))).andExpect(status().isOk()));
        assertThat(res.get("iceServers").get(0).get("urls").get(0).asText()).startsWith("stun:");
        JsonNode turn = res.get("iceServers").get(1);
        assertThat(turn.get("urls").get(0).asText()).isEqualTo("turn:turn.test:3478");
        String user = turn.get("username").asText();
        assertThat(user).endsWith(":" + a);
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec("s3cret".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        assertThat(turn.get("credential").asText()).isEqualTo(Base64.getEncoder().encodeToString(mac.doFinal(user.getBytes(StandardCharsets.UTF_8))));
        assertThat(Long.parseLong(user.split(":")[0])).isGreaterThan(System.currentTimeMillis() / 1000);
    }

    @Test
    void matchCreatedEventUnlocksChat() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        post(a, "/api/v1/chat/conversations", "{\"peerId\":\"" + b + "\"}").andExpect(status().isForbidden());
        matchConsumer.handle("not json");   // ignored, no exception
        matchConsumer.handle("{\"eventType\":\"MatchCreated\",\"payload\":{\"matchId\":\"m1\",\"userAId\":\"" + a + "\",\"userBId\":\"" + b + "\"}}");
        matchConsumer.handle("{\"eventType\":\"MatchCreated\",\"payload\":{\"matchId\":\"m1\",\"userAId\":\"" + a + "\",\"userBId\":\"" + b + "\"}}");  // redelivery
        post(b, "/api/v1/chat/conversations", "{\"peerId\":\"" + a + "\"}").andExpect(status().isOk());
        mvc.perform(get("/api/v1/chat/conversations").with(as(a))).andExpect(jsonPath("$.length()").value(1));
    }
}
