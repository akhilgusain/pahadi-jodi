package in.pahadijodi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.notification.domain.*;
import in.pahadijodi.notification.events.NotificationEventsConsumer;
import in.pahadijodi.notification.service.DeliveryWorker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestJwt.Config.class, NotificationFlowTest.ClockConfig.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:notiftest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.kafka.consumers.enabled=false", "pj.outbox.relay.enabled=false",
        "pj.notification.worker.enabled=false",
        // the fake directory derives email = <userId>@example.invalid, so this user's email always fails
        "pj.notification.fake.fail-addresses=00000000-0000-0000-0000-00000000fa11@example.invalid"})
class NotificationFlowTest {
    static final Instant NOON_IST = Instant.parse("2026-06-10T06:30:00Z");       // 12:00 IST
    static final Instant NIGHT_IST = Instant.parse("2026-06-10T18:00:00Z");      // 23:30 IST
    static final String T_REGISTERED = "pj.identity.user-registered.v1", T_INTEREST = "pj.interest.sent.v1", T_PAYMENT = "pj.payment.captured.v1";

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(NOON_IST); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @Autowired NotificationEventsConsumer consumer;
    @Autowired DeliveryWorker worker;
    @Autowired DeliveryLogRepository deliveries;
    @Autowired NotificationRepository notifications;
    @Autowired DeadLetterRepository deadLetters;
    @Autowired DeviceTokenRepository deviceTokens;

    @BeforeEach
    void resetClock() { clock.set(NOON_IST); }

    static JwtRequestPostProcessor as(UUID u) { return jwt().jwt(j -> j.subject(u.toString())); }

    static String event(String eventId, String payload) {
        return "{\"eventId\":\"" + eventId + "\",\"occurredAt\":\"2026-06-10T06:30:00Z\",\"payload\":" + payload + "}";
    }

    private void interest(UUID to, String interestId) {
        consumer.handle(T_INTEREST, event("ev-" + interestId, "{\"interestId\":\"" + interestId + "\",\"fromUserId\":\"" + UUID.randomUUID() + "\",\"toUserId\":\"" + to + "\"}"));
    }

    private void welcome(UUID u) { consumer.handle(T_REGISTERED, event("ev-reg-" + u, "{\"userId\":\"" + u + "\"}")); }

    private List<DeliveryLog> logs(UUID user, Channel ch) {
        return deliveries.findTop50ByUserIdOrderByCreatedAtDesc(user).stream().filter(d -> d.getChannel() == ch).toList();
    }

    private JsonNode body(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }

    private ResultActions putPrefs(UUID u, String content) throws Exception {
        return mvc.perform(put("/api/v1/notifications/preferences").with(as(u)).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void welcomeCreatesInboxItemSendsEmailAndIsIdempotent() throws Exception {
        UUID u = UUID.randomUUID();
        welcome(u);
        welcome(u);   // Kafka redelivery
        mvc.perform(get("/api/v1/notifications").with(as(u)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].type").value("WELCOME"))
                .andExpect(jsonPath("$.items[0].title").value("Welcome to Pahadi Jodi"))
                .andExpect(jsonPath("$.items[0].data.deepLink").value("/profile"))
                .andExpect(jsonPath("$.items[0].read").value(false));
        mvc.perform(get("/api/v1/notifications/unread-count").with(as(u))).andExpect(jsonPath("$.unread").value(1));

        assertThat(logs(u, Channel.EMAIL)).singleElement().satisfies(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PENDING));
        worker.runDue();
        assertThat(logs(u, Channel.EMAIL)).singleElement().satisfies(d -> {
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.SENT);
            assertThat(d.getProviderRef()).startsWith("fake-email-");
            assertThat(d.getAttempts()).isEqualTo(1);
        });
        mvc.perform(get("/api/v1/notifications/deliveries").with(as(u))).andExpect(jsonPath("$[0].channel").value("EMAIL"));
    }

    @Test
    void hindiTemplatesFollowLocalePreference() throws Exception {
        UUID u = UUID.randomUUID();
        putPrefs(u, "{\"inApp\":true,\"email\":true,\"sms\":true,\"push\":true,\"whatsapp\":false,\"quietStart\":22,\"quietEnd\":8,\"locale\":\"hi\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.locale").value("hi"));
        welcome(u);
        mvc.perform(get("/api/v1/notifications").with(as(u))).andExpect(jsonPath("$.items[0].title").value("पहाड़ी जोड़ी में आपका स्वागत है"));
    }

    @Test
    void preferencesDefaultsAndValidation() throws Exception {
        UUID u = UUID.randomUUID();
        mvc.perform(get("/api/v1/notifications/preferences").with(as(u)))
                .andExpect(jsonPath("$.push").value(true)).andExpect(jsonPath("$.whatsapp").value(false))
                .andExpect(jsonPath("$.quietStart").value(22)).andExpect(jsonPath("$.quietEnd").value(8)).andExpect(jsonPath("$.locale").value("en"));
        putPrefs(u, "{\"inApp\":true,\"email\":true,\"sms\":true,\"push\":true,\"whatsapp\":false,\"locale\":\"fr\"}").andExpect(status().isUnprocessableEntity());
        putPrefs(u, "{\"inApp\":true,\"email\":true,\"sms\":true,\"push\":true,\"whatsapp\":false,\"quietStart\":22,\"locale\":\"en\"}").andExpect(status().isUnprocessableEntity());
        putPrefs(u, "{\"inApp\":true,\"email\":true,\"sms\":true,\"push\":true,\"whatsapp\":false,\"quietStart\":25,\"quietEnd\":3,\"locale\":\"en\"}").andExpect(status().isUnprocessableEntity());
        putPrefs(u, "{\"inApp\":true,\"email\":true,\"sms\":true,\"push\":true,\"whatsapp\":false,\"locale\":\"en\"}")   // quiet hours switched off
                .andExpect(status().isOk()).andExpect(jsonPath("$.quietStart").doesNotExist());
        mvc.perform(get("/api/v1/notifications/preferences").with(as(u))).andExpect(jsonPath("$.quietStart").doesNotExist());
    }

    @Test
    void channelOptOutSuppressesButTransactionalEmailIsMandatory() throws Exception {
        UUID u = UUID.randomUUID();
        putPrefs(u, "{\"inApp\":true,\"email\":false,\"sms\":true,\"push\":false,\"whatsapp\":false,\"quietStart\":null,\"quietEnd\":null,\"locale\":\"en\"}").andExpect(status().isOk());
        interest(u, "opt-1");
        assertThat(logs(u, Channel.PUSH)).singleElement().satisfies(d -> {
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.SUPPRESSED);
            assertThat(d.getSuppressedReason()).isEqualTo("PREF_DISABLED");
        });
        assertThat(logs(u, Channel.EMAIL)).singleElement().satisfies(d -> assertThat(d.getSuppressedReason()).isEqualTo("PREF_DISABLED"));
        welcome(u);   // transactional: email still goes out despite email=false
        assertThat(logs(u, Channel.EMAIL)).anySatisfy(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PENDING));
    }

    @Test
    void quietHoursDeferEngagementButNotTransactional() throws Exception {
        UUID u = UUID.randomUUID();
        clock.set(NIGHT_IST);
        deviceTokens.save(new DeviceToken(u, "tok-" + u, Platform.ANDROID, clock.instant()));
        interest(u, "quiet-1");
        consumer.handle(T_PAYMENT, event("ev-pay-" + u, "{\"paymentId\":\"pay-" + u + "\",\"userId\":\"" + u + "\",\"amountMinor\":49900,\"currency\":\"INR\"}"));

        // inbox item exists immediately (in-app is never deferred)
        mvc.perform(get("/api/v1/notifications").with(as(u))).andExpect(jsonPath("$.total").value(2));
        Instant morning = Instant.parse("2026-06-11T02:30:00Z");   // 08:00 IST
        assertThat(logs(u, Channel.PUSH)).singleElement().satisfies(d -> {
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(d.getNextAttemptAt()).isEqualTo(morning);
        });
        // transactional payment SMS/email are due now
        assertThat(logs(u, Channel.SMS)).singleElement().satisfies(d -> assertThat(d.getNextAttemptAt()).isEqualTo(NIGHT_IST));

        worker.runDue();   // sends only what is due: payment email + sms, not the engagement push/email
        assertThat(logs(u, Channel.SMS)).singleElement().satisfies(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.SENT));
        assertThat(logs(u, Channel.PUSH)).singleElement().satisfies(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PENDING));

        clock.set(morning.plusSeconds(60));
        worker.runDue();
        assertThat(logs(u, Channel.PUSH)).singleElement().satisfies(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.SENT));
    }

    @Test
    void dailyCapsApplyToEngagementOnly() throws Exception {
        UUID u = UUID.randomUUID();
        for (int i = 1; i <= 7; i++) interest(u, "cap-" + i);
        assertThat(logs(u, Channel.PUSH).stream().filter(d -> d.getStatus() == DeliveryStatus.PENDING)).hasSize(6);
        assertThat(logs(u, Channel.PUSH).stream().filter(d -> "DAILY_CAP".equals(d.getSuppressedReason()))).hasSize(1);
        assertThat(logs(u, Channel.EMAIL).stream().filter(d -> d.getStatus() == DeliveryStatus.PENDING)).hasSize(2);
        assertThat(logs(u, Channel.EMAIL).stream().filter(d -> "DAILY_CAP".equals(d.getSuppressedReason()))).hasSize(5);
        // all 7 still land in the inbox
        mvc.perform(get("/api/v1/notifications?size=50").with(as(u))).andExpect(jsonPath("$.total").value(7));
        // next IST day the cap resets
        clock.advance(Duration.ofDays(1));
        interest(u, "cap-8");
        assertThat(logs(u, Channel.PUSH).stream().filter(d -> d.getCreatedAt().equals(clock.instant()))).singleElement()
                .satisfies(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PENDING));
    }

    @Test
    void pushNeedsADeviceAndInvalidTokensArePruned() throws Exception {
        UUID noDevice = UUID.randomUUID(), withDevices = UUID.randomUUID();
        interest(noDevice, "nd-1");
        interest(withDevices, "wd-1");
        deviceTokens.save(new DeviceToken(withDevices, "invalid-old", Platform.ANDROID, clock.instant()));
        deviceTokens.save(new DeviceToken(withDevices, "good-1", Platform.IOS, clock.instant()));
        worker.runDue();
        assertThat(logs(noDevice, Channel.PUSH)).singleElement().satisfies(d -> assertThat(d.getSuppressedReason()).isEqualTo("NO_DEVICE"));
        assertThat(logs(withDevices, Channel.PUSH)).singleElement().satisfies(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.SENT));
        assertThat(deviceTokens.findByUserIdOrderByLastSeenAtDesc(withDevices)).extracting(DeviceToken::getToken).containsExactly("good-1");
    }

    @Test
    void failedDeliveriesRetryWithBackoffThenDeadLetter() throws Exception {
        UUID failing = UUID.fromString("00000000-0000-0000-0000-00000000fa11");
        welcome(failing);
        long[] expectedDelays = {30, 60, 120, 240};   // base 30s * 2^(attempt-1)
        for (int attempt = 1; attempt <= 4; attempt++) {
            worker.runDue();
            DeliveryLog d = logs(failing, Channel.EMAIL).get(0);
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(d.getAttempts()).isEqualTo(attempt);
            assertThat(d.getLastError()).contains("simulated provider outage");
            assertThat(d.getNextAttemptAt()).isEqualTo(clock.instant().plusSeconds(expectedDelays[attempt - 1]));
            assertThat(worker.runDue()).isZero();                    // not due yet: nothing happens
            clock.advance(Duration.ofSeconds(expectedDelays[attempt - 1]));
        }
        worker.runDue();   // 5th and final attempt
        DeliveryLog d = logs(failing, Channel.EMAIL).get(0);
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(d.getAttempts()).isEqualTo(5);
        assertThat(deadLetters.findByUserId(failing)).singleElement().satisfies(dl -> {
            assertThat(dl.getChannel()).isEqualTo(Channel.EMAIL);
            assertThat(dl.getDeliveryId()).isEqualTo(d.getId());
        });
        // the in-app inbox item is unaffected by the failed email
        mvc.perform(get("/api/v1/notifications").with(as(failing))).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void inboxPagingReadStateAndOwnership() throws Exception {
        UUID u = UUID.randomUUID(), other = UUID.randomUUID();
        for (int i = 1; i <= 3; i++) { interest(u, "inbox-" + i); clock.advance(Duration.ofMinutes(1)); }
        JsonNode page1 = body(mvc.perform(get("/api/v1/notifications?size=2").with(as(u))));
        assertThat(page1.get("items")).hasSize(2);
        assertThat(page1.get("hasMore").asBoolean()).isTrue();
        assertThat(page1.get("total").asInt()).isEqualTo(3);
        mvc.perform(get("/api/v1/notifications?size=2&page=1").with(as(u))).andExpect(jsonPath("$.items.length()").value(1));

        String id = page1.get("items").get(0).get("id").asText();
        post(other, "/api/v1/notifications/" + id + "/read").andExpect(status().isNotFound());       // someone else's notification
        post(u, "/api/v1/notifications/" + id + "/read").andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/api/v1/notifications/unread-count").with(as(u))).andExpect(jsonPath("$.unread").value(2));
        mvc.perform(get("/api/v1/notifications?unreadOnly=true").with(as(u))).andExpect(jsonPath("$.total").value(2));
        post(u, "/api/v1/notifications/read-all").andExpect(jsonPath("$.updated").value(2));
        mvc.perform(get("/api/v1/notifications/unread-count").with(as(u))).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/v1/notifications").with(as(other))).andExpect(jsonPath("$.total").value(0));
    }

    private ResultActions post(UUID u, String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).with(as(u)));
    }

    @Test
    void deviceRegistrationMovesTokensBetweenAccountsAndCapsPerUser() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        String reg = "{\"token\":\"shared-token\",\"platform\":\"ANDROID\"}";
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/notifications/devices").with(as(a)).contentType(MediaType.APPLICATION_JSON).content(reg)).andExpect(status().isNoContent());
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/notifications/devices").with(as(b)).contentType(MediaType.APPLICATION_JSON).content(reg)).andExpect(status().isNoContent());
        assertThat(deviceTokens.findByUserIdOrderByLastSeenAtDesc(a)).isEmpty();
        assertThat(deviceTokens.findByUserIdOrderByLastSeenAtDesc(b)).hasSize(1);
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/notifications/devices").with(as(b)).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"\",\"platform\":\"IOS\"}")).andExpect(status().isUnprocessableEntity());

        mvc.perform(delete("/api/v1/notifications/devices?token=shared-token").with(as(a))).andExpect(status().isNoContent());   // not a's token: no effect
        assertThat(deviceTokens.findByUserIdOrderByLastSeenAtDesc(b)).hasSize(1);
        mvc.perform(delete("/api/v1/notifications/devices?token=shared-token").with(as(b))).andExpect(status().isNoContent());
        assertThat(deviceTokens.findByUserIdOrderByLastSeenAtDesc(b)).isEmpty();

        for (int i = 0; i < 12; i++) {
            mvc.perform(MockMvcRequestBuilders.post("/api/v1/notifications/devices").with(as(a)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"cap-token-" + i + "\",\"platform\":\"WEB\"}")).andExpect(status().isNoContent());
            clock.advance(Duration.ofSeconds(1));
        }
        assertThat(deviceTokens.findByUserIdOrderByLastSeenAtDesc(a)).hasSize(10);
    }

    @Test
    void liveStreamPushesNewNotificationsAndAcceptsTokenOnlyOnStreamPath() throws Exception {
        UUID u = UUID.randomUUID();
        mvc.perform(get("/api/v1/notifications/stream")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/stream?token=garbage")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications?token=" + TestJwt.token(u))).andExpect(status().isUnauthorized());   // ?token= is NOT accepted elsewhere

        MvcResult stream = mvc.perform(get("/api/v1/notifications/stream?token=" + TestJwt.token(u)).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        welcome(u);                                        // committed -> SSE hub pushes AFTER commit
        welcome(UUID.randomUUID());                        // someone else's notification must not leak into this stream
        String sse = stream.getResponse().getContentAsString();
        assertThat(sse).contains("event:ready").contains("event:notification").contains("Welcome to Pahadi Jodi");
        assertThat(sse.split("event:notification", -1)).hasSize(2);   // exactly one notification event
    }
}
