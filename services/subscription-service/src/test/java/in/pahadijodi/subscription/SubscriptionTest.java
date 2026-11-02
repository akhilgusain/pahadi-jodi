package in.pahadijodi.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.outbox.OutboxEvent;
import in.pahadijodi.common.outbox.OutboxRepository;
import in.pahadijodi.subscription.service.PaymentEventsListener;
import in.pahadijodi.subscription.service.SubscriptionService;
import in.pahadijodi.subscription.service.SubscriptionService.CheckResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:subtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false", "pj.kafka.consumers.enabled=false",
        "pj.subscription.dev-endpoints=true"})
class SubscriptionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SubscriptionService service;
    @Autowired PaymentEventsListener listener;
    @Autowired OutboxRepository outbox;

    private static RequestPostProcessor as(UUID id) { return jwt().jwt(j -> j.subject(id.toString())); }

    private long events(String topic, UUID user) {
        return outbox.findAll().stream().filter(e -> e.getTopic().equals(topic) && e.getEnvelope().contains(user.toString())).count();
    }

    private String capturedEvent(UUID user, String plan, String paymentId, String purpose) {
        return "{\"eventType\":\"PaymentCaptured\",\"payload\":{\"userId\":\"" + user + "\",\"planCode\":\"" + plan
                + "\",\"paymentId\":\"" + paymentId + "\",\"purpose\":\"" + purpose + "\"}}";
    }

    // ---------------- catalogue & lazy FREE ----------------
    @Test
    void planCatalogueIsPublicAndMatchesTheMatrix() throws Exception {
        mvc.perform(get("/api/v1/subscriptions/plans")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].code").value("FREE"))
                .andExpect(jsonPath("$[1].priceInrMinor").value(149900))
                .andExpect(jsonPath("$[3].code").value("VIP"))
                .andExpect(jsonPath("$[0].entitlements.length()").value(21))
                .andExpect(jsonPath("$[0].entitlements[?(@.key=='interest.send_per_day')].limit").value(5))
                .andExpect(jsonPath("$[3].entitlements[?(@.key=='support.relationship_manager')].enabled").value(true));
        mvc.perform(get("/api/v1/subscriptions/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void newUserIsFreeLazily() throws Exception {
        UUID u = UUID.randomUUID();
        mvc.perform(get("/api/v1/subscriptions/me").with(as(u))).andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("FREE"))
                .andExpect(jsonPath("$.entitlements.length()").value(21));
        mvc.perform(get("/api/v1/subscriptions/me/entitlements").with(as(u))).andExpect(jsonPath("$.length()").value(21));
    }

    // ---------------- entitlement checks ----------------
    @Test
    void freeQuotaIsConsumedThenDeniedWithUpgradeHint() {
        UUID u = UUID.randomUUID();
        for (int i = 1; i <= 5; i++) {
            CheckResult r = service.check(u, "interest.send_per_day", 1, null);
            assertThat(r.allowed()).isTrue();
            assertThat(r.remaining()).isEqualTo(5 - i);
        }
        CheckResult denied = service.check(u, "interest.send_per_day", 1, null);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.reason()).isEqualTo("QUOTA_EXHAUSTED");
        assertThat(denied.upgradeTo()).isEqualTo("PREMIUM");
        // a denied attempt must not consume anything
        assertThat(service.check(u, "interest.send_per_day", 0, null).remaining()).isZero();
    }

    @Test
    void dailyQuotaResetsAtUtcMidnight() {
        UUID u = UUID.randomUUID();
        Instant day1 = Instant.parse("2026-11-02T10:00:00Z");
        for (int i = 0; i < 5; i++) assertThat(service.check(u, "interest.send_per_day", 1, null, day1).allowed()).isTrue();
        assertThat(service.check(u, "interest.send_per_day", 1, null, day1).allowed()).isFalse();
        assertThat(service.check(u, "interest.send_per_day", 1, null, day1.plus(Duration.ofDays(1))).allowed()).isTrue();
    }

    @Test
    void notIncludedCapAndFlagKinds() {
        UUID u = UUID.randomUUID();
        CheckResult chat = service.check(u, "chat.initiate", 1, null);
        assertThat(chat.allowed()).isFalse();
        assertThat(chat.reason()).isEqualTo("NOT_INCLUDED");
        assertThat(chat.upgradeTo()).isEqualTo("PREMIUM");

        assertThat(service.check(u, "exclusive_matches", 0, null).upgradeTo()).isEqualTo("VIP");       // skips lower tiers
        assertThat(service.check(u, "ads", 0, null).allowed()).isTrue();                                // FREE sees ads

        // CAP: caller supplies how many saved searches exist (FREE ceiling = 1)
        assertThat(service.check(u, "search.saved_searches", 0, 0L).allowed()).isTrue();
        CheckResult cap = service.check(u, "search.saved_searches", 0, 1L);
        assertThat(cap.allowed()).isFalse();
        assertThat(cap.upgradeTo()).isEqualTo("PREMIUM");

        // unlimited
        assertThat(service.check(u, "search.results_per_day", 1, null).remaining()).isEqualTo(49);
    }

    @Test
    void unknownKeyAndCrossUserChecksAreRejected() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        String body = "{\"key\":\"nope\"}";
        mvc.perform(post("/api/v1/subscriptions/entitlements/check").with(as(a)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        String other = "{\"userId\":\"" + b + "\",\"key\":\"interest.send_per_day\"}";
        mvc.perform(post("/api/v1/subscriptions/entitlements/check").with(as(a)).contentType(MediaType.APPLICATION_JSON).content(other))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/subscriptions/entitlements/check")
                        .with(jwt().jwt(j -> j.subject(a.toString())).authorities(new SimpleGrantedAuthority("ROLE_SERVICE")))
                        .contentType(MediaType.APPLICATION_JSON).content(other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void concurrentConsumptionNeverExceedsTheLimit() throws Exception {
        UUID u = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> fs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            fs.add(pool.submit(() -> { go.await(); return service.check(u, "interest.send_per_day", 1, null).allowed(); }));
        }
        go.countDown();
        int allowed = 0;
        for (Future<Boolean> f : fs) if (f.get(30, TimeUnit.SECONDS)) allowed++;
        pool.shutdown();
        assertThat(allowed).isEqualTo(5);                                       // FREE limit, exactly
        assertThat(service.check(u, "interest.send_per_day", 0, null).remaining()).isZero();
    }

    // ---------------- lifecycle ----------------
    @Test
    void activationUpgradesEntitlementsIsIdempotentAndRenewalExtends() throws Exception {
        UUID u = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-11-02T10:00:00Z");
        assertThat(service.activate(u, "PREMIUM", null, "pay-1", t0)).isTrue();
        assertThat(service.activate(u, "PREMIUM", null, "pay-1", t0)).isFalse();        // replay: no-op
        assertThat(events("pj.subscription.activated.v1", u)).isEqualTo(1);
        assertThat(events("pj.subscription.entitlement-changed.v1", u)).isEqualTo(1);

        mvc.perform(get("/api/v1/subscriptions/me").with(as(u)))
                .andExpect(jsonPath("$.planCode").value("PREMIUM")).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.entitlements[?(@.key=='interest.send_per_day')].limit").value(30))
                .andExpect(jsonPath("$.entitlements[?(@.key=='ads')].enabled").value(false));
        assertThat(service.me(u).currentPeriodEnd()).isEqualTo(t0.plus(Duration.ofDays(90)));

        // renewal 10 days later extends from the existing period end, not from "now"
        assertThat(service.activate(u, "PREMIUM", null, "pay-2", t0.plus(Duration.ofDays(10)))).isTrue();
        assertThat(service.me(u).currentPeriodEnd()).isEqualTo(t0.plus(Duration.ofDays(180)));
        assertThat(events("pj.subscription.entitlement-changed.v1", u)).isEqualTo(1);    // tier unchanged: no new event

        // upgrade restarts the period on the new plan
        service.activate(u, "VIP", null, "pay-3", t0.plus(Duration.ofDays(20)));
        assertThat(service.me(u).planCode()).isEqualTo("VIP");
        assertThat(service.me(u).currentPeriodEnd()).isEqualTo(t0.plus(Duration.ofDays(20 + 365)));
    }

    @Test
    void unknownOrFreePlanCannotBePurchased() {
        UUID u = UUID.randomUUID();
        org.junit.jupiter.api.Assertions.assertThrows(in.pahadijodi.common.web.ProblemAdvice.PjException.class,
                () -> service.activate(u, "FREE", null, "p"));
        org.junit.jupiter.api.Assertions.assertThrows(in.pahadijodi.common.web.ProblemAdvice.PjException.class,
                () -> service.activate(u, "GOLD", null, "p"));
    }

    @Test
    void activeThenGraceThenExpiredAndRepurchase() {
        UUID u = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        service.activate(u, "PREMIUM", null, "g-1", t0);                      // period end = t0 + 90d

        assertThat(service.processExpiries(t0.plus(Duration.ofDays(89)))).isZero();

        assertThat(service.processExpiries(t0.plus(Duration.ofDays(91)))).isEqualTo(1);   // -> GRACE
        assertThat(service.me(u).status()).isEqualTo("GRACE");
        assertThat(service.me(u).planCode()).isEqualTo("PREMIUM");            // entitlements kept during grace
        assertThat(service.check(u, "chat.initiate", 0, null).allowed()).isTrue();
        assertThat(events("pj.subscription.grace-started.v1", u)).isEqualTo(1);

        assertThat(service.processExpiries(t0.plus(Duration.ofDays(92)))).isZero();       // still inside grace

        assertThat(service.processExpiries(t0.plus(Duration.ofDays(94)))).isEqualTo(1);   // 90d + 3d grace passed -> EXPIRED
        assertThat(service.me(u).status()).isEqualTo("EXPIRED");
        assertThat(service.me(u).planCode()).isEqualTo("FREE");
        assertThat(service.check(u, "chat.initiate", 0, null).allowed()).isFalse();
        assertThat(events("pj.subscription.expired.v1", u)).isEqualTo(1);

        service.activate(u, "PREMIUM", null, "g-2", t0.plus(Duration.ofDays(100)));       // EXPIRED -> ACTIVE
        assertThat(service.me(u).status()).isEqualTo("ACTIVE");
    }

    @Test
    void renewalDuringGraceRestoresActive() {
        UUID u = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        service.activate(u, "PREMIUM", null, "r-1", t0);
        service.processExpiries(t0.plus(Duration.ofDays(91)));
        assertThat(service.me(u).status()).isEqualTo("GRACE");
        service.activate(u, "PREMIUM", null, "r-2", t0.plus(Duration.ofDays(92)));        // retry charge succeeded
        assertThat(service.me(u).status()).isEqualTo("ACTIVE");
        assertThat(service.me(u).graceEndsAt()).isNull();
    }

    @Test
    void cancelAtPeriodEndSkipsGraceAndCancelNowIsImmediate() throws Exception {
        UUID soft = UUID.randomUUID(), hard = UUID.randomUUID(), free = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

        mvc.perform(post("/api/v1/subscriptions/me/cancel").with(as(free))).andExpect(status().isConflict());   // nothing to cancel

        service.activate(soft, "PREMIUM", null, "s-1", t0);
        mvc.perform(post("/api/v1/subscriptions/me/cancel").with(as(soft))).andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true)).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/v1/subscriptions/me/resume").with(as(soft))).andExpect(jsonPath("$.cancelAtPeriodEnd").value(false));
        mvc.perform(post("/api/v1/subscriptions/me/cancel").with(as(soft)));
        service.processExpiries(t0.plus(Duration.ofDays(91)));
        assertThat(service.me(soft).status()).isEqualTo("EXPIRED");                       // no grace for a deliberate cancel

        service.activate(hard, "VIP", null, "h-1", t0);
        service.cancelNow(hard, "REFUND");
        assertThat(service.me(hard).status()).isEqualTo("CANCELED");
        assertThat(service.me(hard).planCode()).isEqualTo("FREE");
        assertThat(events("pj.subscription.canceled.v1", hard)).isEqualTo(1);
    }

    // ---------------- payment events ----------------
    @Test
    void paymentEventsActivateOncePerPaymentAndIgnoreNoise() {
        UUID u = UUID.randomUUID();
        listener.handleCaptured(capturedEvent(u, "PREMIUM_PLUS", "pay-x", "SUBSCRIPTION_NEW"));
        listener.handleCaptured(capturedEvent(u, "PREMIUM_PLUS", "pay-x", "SUBSCRIPTION_NEW"));   // Kafka redelivery
        assertThat(service.me(u).planCode()).isEqualTo("PREMIUM_PLUS");
        assertThat(events("pj.subscription.activated.v1", u)).isEqualTo(1);

        UUID boost = UUID.randomUUID();
        listener.handleCaptured(capturedEvent(boost, "PREMIUM", "pay-b", "BOOST"));              // not a subscription purchase
        listener.handleCaptured("not json");
        listener.handleCaptured("{\"payload\":{\"userId\":\"" + boost + "\"}}");                  // no planCode
        listener.handleCaptured(capturedEvent(boost, "BOGUS_PLAN", "pay-c", "SUBSCRIPTION_NEW"));
        assertThat(service.me(boost).planCode()).isEqualTo("FREE");

        listener.handleRefunded("{\"payload\":{\"userId\":\"" + u + "\"}}");
        assertThat(service.me(u).status()).isEqualTo("CANCELED");
    }

    @Test
    void devActivateEndpointWorksWhenEnabled() throws Exception {
        UUID u = UUID.randomUUID();
        mvc.perform(post("/api/v1/subscriptions/dev/activate").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"VIP\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planCode").value("VIP"));
        mvc.perform(post("/api/v1/subscriptions/dev/activate").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"FREE\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
