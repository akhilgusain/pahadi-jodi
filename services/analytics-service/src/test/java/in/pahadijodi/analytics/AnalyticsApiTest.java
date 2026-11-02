package in.pahadijodi.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import in.pahadijodi.analytics.service.IngestService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:analytics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false", "pj.kafka.consumers.enabled=false",
        "pj.analytics.dev-ingest.enabled=true", "pj.analytics.pseudonym-key=test-key"})
class AnalyticsApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IngestService ingestService;

    static final String A = "0b8f4c0e-6a54-4b52-9f0a-1d1d6d6f7a01", B = "0b8f4c0e-6a54-4b52-9f0a-1d1d6d6f7a02", C = "0b8f4c0e-6a54-4b52-9f0a-1d1d6d6f7a03";
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    static RequestPostProcessor admin() { return jwt().jwt(j -> j.subject(A)).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")); }
    static RequestPostProcessor finance() { return jwt().jwt(j -> j.subject(B)).authorities(new SimpleGrantedAuthority("ROLE_FINANCE")); }
    static RequestPostProcessor member(String id) { return jwt().jwt(j -> j.subject(id)); }

    static LocalDate d() { return LocalDate.now(IST).minusDays(1); }                 // the seeded business day
    static String at(LocalDate day) { return day.atTime(6, 0).toInstant(ZoneOffset.UTC).toString(); }   // 11:30 IST same day

    static String ev(String topic, String id, LocalDate day, String payload) {
        return "{\"topic\":\"" + topic + "\",\"eventId\":\"" + id + "\",\"occurredAt\":\"" + at(day) + "\",\"payload\":" + payload + "}";
    }

    String seedBody() {
        LocalDate day = d();
        return "{\"events\":[" + String.join(",",
                ev("pj.identity.user-registered.v1", "t-reg-a", day, "{\"userId\":\"" + A + "\"}"),
                ev("pj.identity.user-registered.v1", "t-reg-b", day, "{\"userId\":\"" + B + "\"}"),
                ev("pj.identity.user-registered.v1", "t-reg-c", day, "{\"userId\":\"" + C + "\"}"),
                ev("pj.profile.updated.v1", "t-pr-a", day, "{\"userId\":\"" + A + "\",\"completeness\":90,\"community\":\"GARHWALI\",\"district\":\"PAURI_GARHWAL\"}"),
                ev("pj.profile.updated.v1", "t-pr-b", day, "{\"userId\":\"" + B + "\",\"completeness\":85,\"community\":\"KUMAONI\",\"district\":\"ALMORA\"}"),
                ev("pj.profile.updated.v1", "t-pr-c", day, "{\"userId\":\"" + C + "\",\"completeness\":40}"),
                ev("pj.matching.interest.sent.v1", "t-int-ab", day, "{\"fromUserId\":\"" + A + "\",\"toUserId\":\"" + B + "\"}"),
                ev("pj.matching.interest.sent.v1", "t-int-ba", day, "{\"fromUserId\":\"" + B + "\",\"toUserId\":\"" + A + "\"}"),
                ev("pj.matching.interest.sent.v1", "t-int-ac", day, "{\"fromUserId\":\"" + A + "\",\"toUserId\":\"" + C + "\"}"),
                ev("pj.matching.match.created.v1", "t-match", day, "{\"userA\":\"" + A + "\",\"userB\":\"" + B + "\"}"),
                ev("pj.chat.message.sent.v1", "t-msg", day, "{\"senderId\":\"" + A + "\",\"conversationId\":\"c1\"}"),
                ev("pj.payment.payment.captured.v1", "t-pay-a", day, "{\"userId\":\"" + A + "\",\"amountPaise\":99900,\"currency\":\"INR\",\"planCode\":\"PREMIUM_PLUS\"}"),
                ev("pj.payment.payment.captured.v1", "t-pay-b", day, "{\"userId\":\"" + B + "\",\"amount\":499.0,\"currency\":\"INR\",\"plan\":\"PREMIUM\"}"),
                ev("pj.profile.profile.viewed.v1", "t-view-1", day, "{\"viewerId\":\"" + B + "\",\"profileUserId\":\"" + A + "\"}"),
                ev("pj.profile.profile.viewed.v1", "t-view-2", day, "{\"viewerId\":\"" + B + "\",\"profileUserId\":\"" + A + "\"}"),
                ev("pj.profile.profile.viewed.v1", "t-view-3", day, "{\"viewerId\":\"" + C + "\",\"profileUserId\":\"" + A + "\"}"),
                ev("pj.identity.user.logged-in.v1", "t-login-c", day, "{\"userId\":\"" + C + "\"}"),
                // duplicate delivery of an already-seen event (Kafka at-least-once)
                ev("pj.identity.user-registered.v1", "t-reg-a", day, "{\"userId\":\"" + A + "\"}")) + "]}";
    }

    @BeforeEach
    void seed() throws Exception {
        mvc.perform(post("/api/v1/analytics/dev/ingest").with(admin()).contentType(MediaType.APPLICATION_JSON).content(seedBody()))
                .andExpect(status().isOk());
    }

    @Test
    void ingestIsIdempotentAndReportsDuplicates() throws Exception {
        mvc.perform(post("/api/v1/analytics/dev/ingest").with(admin()).contentType(MediaType.APPLICATION_JSON).content(seedBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(18))
                .andExpect(jsonPath("$.rowsStored").value(0))
                .andExpect(jsonPath("$.duplicates").value(19));   // 18 events; the match event is stored as two rows
    }

    @Test
    void summaryCountsEventsAndRevenueForTheDay() throws Exception {
        mvc.perform(get("/api/v1/analytics/admin/summary?from=" + d() + "&to=" + d()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.REGISTERED").value(3))
                .andExpect(jsonPath("$.totals.PROFILE_COMPLETE").value(2))
                .andExpect(jsonPath("$.totals.PROFILE_UPDATED").value(1))
                .andExpect(jsonPath("$.totals.INTEREST_SENT").value(3))
                .andExpect(jsonPath("$.totals.MATCH_CREATED").value(1))
                .andExpect(jsonPath("$.totals.MESSAGE_SENT").value(1))
                .andExpect(jsonPath("$.totals.PAYMENT_CAPTURED").value(2))
                .andExpect(jsonPath("$.totals.PROFILE_VIEWED").value(3))
                .andExpect(jsonPath("$.totals.OTHER").value(1))
                .andExpect(jsonPath("$.totals.REVENUE_INR").value(149800))
                .andExpect(jsonPath("$.uniqueActiveUsers").value(3))
                .andExpect(jsonPath("$.daily.length()").value(1))
                .andExpect(jsonPath("$.daily[0].metrics.ACTIVE_USERS").value(3));
    }

    @Test
    void funnelIsStrictAndFollowsTheDocumentedStages() throws Exception {
        // cohort = A, B, C. profile complete: A, B. interest: A, B. matched: A, B. chat: A. paid: A (B paid but skipped chat)
        mvc.perform(get("/api/v1/analytics/admin/funnel?from=" + d() + "&to=" + d()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages[?(@.stage=='REGISTERED')].users").value(3))
                .andExpect(jsonPath("$.stages[?(@.stage=='PROFILE_COMPLETE')].users").value(2))
                .andExpect(jsonPath("$.stages[?(@.stage=='INTEREST_SENT')].users").value(2))
                .andExpect(jsonPath("$.stages[?(@.stage=='MATCH_CREATED')].users").value(2))
                .andExpect(jsonPath("$.stages[?(@.stage=='MESSAGE_SENT')].users").value(1))
                .andExpect(jsonPath("$.stages[?(@.stage=='PAYMENT_CAPTURED')].users").value(1))
                .andExpect(jsonPath("$.stages[?(@.stage=='PAYMENT_CAPTURED')].reachedIgnoringOrder").value(2))
                .andExpect(jsonPath("$.stages[?(@.stage=='PROFILE_COMPLETE')].conversionFromPrevious").value(0.6667))
                .andExpect(jsonPath("$.stages[?(@.stage=='MESSAGE_SENT')].conversionFromPrevious").value(0.5))
                .andExpect(jsonPath("$.stages[?(@.stage=='PAYMENT_CAPTURED')].conversionFromStart").value(0.3333));
        // a cohort window with no registrations is empty, not an error
        mvc.perform(get("/api/v1/analytics/admin/funnel?from=2020-01-01&to=2020-01-31").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stages[0].users").value(0))
                .andExpect(jsonPath("$.stages[1].conversionFromPrevious").value(0.0));
    }

    @Test
    void revenueByCurrencyPlanAndDay() throws Exception {
        mvc.perform(get("/api/v1/analytics/admin/revenue?from=" + d() + "&to=" + d()).with(finance()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountsAreMinorUnits").value(true))
                .andExpect(jsonPath("$.totalByCurrency.INR").value(149800))
                .andExpect(jsonPath("$.byPlan.INR.PREMIUM_PLUS").value(99900))
                .andExpect(jsonPath("$.byPlan.INR.PREMIUM").value(49900))
                .andExpect(jsonPath("$.byDay['" + d() + "'].INR").value(149800))
                .andExpect(jsonPath("$.paymentsCaptured").value(2));
    }

    @Test
    void retentionCohortsByRegistrationWeek() throws Exception {
        String weekStart = d().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
        mvc.perform(get("/api/v1/analytics/admin/retention?cohorts=4").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohorts[?(@.cohortWeekStart=='" + weekStart + "')].size").value(3))
                // A, B and C all did something other than register in their registration week
                .andExpect(jsonPath("$.cohorts[?(@.cohortWeekStart=='" + weekStart + "')].retentionPercentByWeek[0]").value(100.0));
        mvc.perform(get("/api/v1/analytics/admin/retention?cohorts=0").with(admin())).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void closedDaysAreServedFromTheRollupAndRefreshedOnDemand() throws Exception {
        LocalDate day = LocalDate.now(IST).minusDays(5);
        String x = "0b8f4c0e-6a54-4b52-9f0a-1d1d6d6f7a0x";
        ingestService.ingest("pj.chat.message.sent.v1", "{\"eventId\":\"roll-1\",\"occurredAt\":\"" + at(day) + "\",\"payload\":{\"senderId\":\"" + x + "\"}}");
        ingestService.ingest("pj.chat.message.sent.v1", "{\"eventId\":\"roll-2\",\"occurredAt\":\"" + at(day) + "\",\"payload\":{\"senderId\":\"" + x + "\"}}");
        // before any rollup the summary is computed live
        mvc.perform(get("/api/v1/analytics/admin/summary?from=" + day + "&to=" + day).with(admin()))
                .andExpect(jsonPath("$.totals.MESSAGE_SENT").value(2));
        mvc.perform(post("/api/v1/analytics/admin/rollup?day=" + day).with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.MESSAGE_SENT").value(2));
        // a late event arrives after the day was rolled up: the closed day still reports the rollup...
        ingestService.ingest("pj.chat.message.sent.v1", "{\"eventId\":\"roll-3\",\"occurredAt\":\"" + at(day) + "\",\"payload\":{\"senderId\":\"" + x + "\"}}");
        mvc.perform(get("/api/v1/analytics/admin/summary?from=" + day + "&to=" + day).with(admin()))
                .andExpect(jsonPath("$.totals.MESSAGE_SENT").value(2));
        // ...until the next rollup picks it up
        mvc.perform(post("/api/v1/analytics/admin/rollup?day=" + day).with(admin())).andExpect(jsonPath("$.MESSAGE_SENT").value(3));
        mvc.perform(get("/api/v1/analytics/admin/summary?from=" + day + "&to=" + day).with(admin()))
                .andExpect(jsonPath("$.totals.MESSAGE_SENT").value(3));
    }

    @Test
    void memberInsightsAreScopedToTheCaller() throws Exception {
        mvc.perform(get("/api/v1/analytics/me/insights?days=7").with(member(A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowDays").value(7))
                .andExpect(jsonPath("$.profileViews.total").value(3))
                .andExpect(jsonPath("$.profileViews.unique").value(2))
                .andExpect(jsonPath("$.profileViews.daily.length()").value(7))
                .andExpect(jsonPath("$.interestsReceived.total").value(1))
                .andExpect(jsonPath("$.interestsSent.total").value(2))
                .andExpect(jsonPath("$.matches.total").value(1));
        // C has no profile views and received one interest (from A); they never see A's numbers
        mvc.perform(get("/api/v1/analytics/me/insights?days=7").with(member(C)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileViews.total").value(0))
                .andExpect(jsonPath("$.interestsReceived.total").value(1))
                .andExpect(jsonPath("$.interestsSent.total").value(0));
        mvc.perform(get("/api/v1/analytics/me/insights?days=500").with(member(A))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void insightsRequirePremiumPlus() throws Exception {
        mvc.perform(get("/api/v1/analytics/me/insights").with(jwt().jwt(j -> j.subject(A).claim("plan", "FREE"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PLAN_UPGRADE_REQUIRED"));
        mvc.perform(get("/api/v1/analytics/me/insights").with(jwt().jwt(j -> j.subject(A).claim("plan", "PREMIUM"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/analytics/me/insights").with(jwt().jwt(j -> j.subject(A).claim("plan", "VIP")))).andExpect(status().isOk());
    }

    @Test
    void adminEndpointsAreRoleProtected() throws Exception {
        mvc.perform(get("/api/v1/analytics/admin/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/analytics/admin/summary").with(member(A))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/analytics/admin/revenue").with(member(A))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/analytics/admin/summary").with(finance())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/analytics/admin/rollup?day=" + d()).with(finance())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/analytics/dev/ingest").with(member(A)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[]}")).andExpect(status().is4xxClientError());
        // bad ranges
        mvc.perform(get("/api/v1/analytics/admin/summary?from=2026-02-01&to=2026-01-01").with(admin())).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/analytics/admin/summary?from=2020-01-01&to=2026-01-01").with(admin())).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void noRawIdentifiersAreStored() {
        Long leaks = jdbc.sql("select count(*) from analytics_event where user_hash like '%0b8f4c0e%' or target_hash like '%0b8f4c0e%' "
                + "or event_id like '%0b8f4c0e%' or topic like '%0b8f4c0e%'").query(Long.class).single();
        assertEquals(0L, leaks);
        Long badLen = jdbc.sql("select count(*) from analytics_event where user_hash <> '-' and length(user_hash) <> 64").query(Long.class).single();
        assertEquals(0L, badLen);
    }

    @Test
    void malformedKafkaMessagesAreSkippedNotThrown() {
        assertEquals(1, ingestService.ingest("pj.chat.message.sent.v1", "not json").ignored());
        assertEquals(1, ingestService.ingest("pj.chat.message.sent.v1", "[1,2]").ignored());
        assertEquals(1, ingestService.ingest("pj.chat.message.sent.v1.dlq", "{}").ignored());
        // a bare payload without an envelope still works and de-duplicates on content
        var first = ingestService.ingest("pj.chat.message.sent.v1", "{\"senderId\":\"bare-user\"}");
        var second = ingestService.ingest("pj.chat.message.sent.v1", "{\"senderId\":\"bare-user\"}");
        assertEquals(1, first.stored());
        assertEquals(1, second.duplicates());
    }
}
