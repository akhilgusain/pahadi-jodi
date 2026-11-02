package in.pahadijodi.analytics;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.analytics.mapping.EventMapper;
import in.pahadijodi.analytics.mapping.Pseudonymizer;
import in.pahadijodi.analytics.model.FactRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventMapperTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Pseudonymizer PSEUDO = new Pseudonymizer("unit-test-key", true);
    static final EventMapper MAPPER = new EventMapper(PSEUDO, "Asia/Kolkata");

    static JsonNode p(String json) {
        try { return JSON.readTree(json); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void classifiesTopicsAcrossNamingVariants() {
        assertEquals("REGISTERED", MAPPER.classify("pj.identity.user-registered.v1"));
        assertEquals("REGISTERED", MAPPER.classify("pj.identity.user.registered.v1"));
        assertEquals("OTHER", MAPPER.classify("pj.identity.device.registered.v1"));
        assertEquals("PROFILE_VIEWED", MAPPER.classify("pj.profile.profile.viewed.v1"));
        assertEquals("PROFILE_UPDATED", MAPPER.classify("pj.profile.updated.v1"));
        assertEquals("PROFILE_UPDATED", MAPPER.classify("pj.profile.profile.created.v1"));
        assertEquals("OTHER", MAPPER.classify("pj.profile.profile.verified.v1"));
        assertEquals("INTEREST_SENT", MAPPER.classify("pj.matching.interest.sent.v1"));
        assertEquals("INTEREST_ACCEPTED", MAPPER.classify("pj.matching.interest.accepted.v1"));
        assertEquals("MATCH_CREATED", MAPPER.classify("pj.matching.match.created.v1"));
        assertEquals("OTHER", MAPPER.classify("pj.matching.recommendations.generated.v1"));
        assertEquals("MESSAGE_SENT", MAPPER.classify("pj.chat.message.sent.v1"));
        assertEquals("OTHER", MAPPER.classify("pj.chat.message.read.v1"));
        assertEquals("SUBSCRIPTION_ACTIVATED", MAPPER.classify("pj.subscription.subscription.activated.v1"));
        assertEquals("PAYMENT_CAPTURED", MAPPER.classify("pj.payment.payment.captured.v1"));
        assertEquals("OTHER", MAPPER.classify("pj.payment.payment.failed.v1"));
    }

    @Test
    void ignoresNonPjAndOperationalTopics() {
        assertTrue(EventMapper.ignoredTopic("other.topic"));
        assertTrue(EventMapper.ignoredTopic("pj.chat.message.sent.v1.dlq"));
        assertTrue(EventMapper.ignoredTopic("pj.chat.message.sent.v1.retry-1"));
        assertFalse(EventMapper.ignoredTopic("pj.chat.message.sent.v1"));
    }

    @Test
    void pseudonymsAreDeterministicKeyedAndNeverTheRawId() {
        String id = "0b8f4c0e-6a54-4b52-9f0a-1d1d6d6f7a01";
        String h = PSEUDO.hash(id);
        assertEquals(64, h.length());
        assertEquals(h, PSEUDO.hash(id.toUpperCase()), "case-insensitive");
        assertNotEquals(h, new Pseudonymizer("another-key", true).hash(id), "key changes the pseudonym");
        assertFalse(h.contains("0b8f4c0e"));
        assertThrows(IllegalStateException.class, () -> new Pseudonymizer("", true), "prod must supply a key");
    }

    @Test
    void matchCreatesTwoMirroredRowsSharingOneEventId() {
        List<FactRow> rows = MAPPER.map("pj.matching.match.created.v1", "evt-1", Instant.parse("2026-03-01T20:00:00Z"),
                p("{\"userA\":\"a\",\"userB\":\"b\"}"));
        assertEquals(2, rows.size());
        assertEquals(rows.get(0).userHash(), rows.get(1).targetHash());
        assertEquals(rows.get(1).userHash(), rows.get(0).targetHash());
        assertEquals("evt-1", rows.get(0).eventId());
        // 20:00Z is already the next calendar day in IST (UTC+5:30): business day follows the configured zone
        assertEquals(LocalDate.of(2026, 3, 2), rows.get(0).day());
        // array form
        assertEquals(2, MAPPER.map("pj.matching.match.created.v1", "evt-2", Instant.now(), p("{\"participants\":[\"x\",\"y\"]}")).size());
    }

    @Test
    void paymentAmountsAreNormalisedToMinorUnits() {
        FactRow paise = MAPPER.map("pj.payment.payment.captured.v1", "e", Instant.now(),
                p("{\"userId\":\"u\",\"amountPaise\":99900,\"currency\":\"inr\",\"planCode\":\"premium_plus\"}")).get(0);
        assertEquals(99900L, paise.amountMinor());
        assertEquals("INR", paise.currency());
        assertEquals("PREMIUM_PLUS", paise.plan());
        FactRow major = MAPPER.map("pj.payment.payment.captured.v1", "e", Instant.now(), p("{\"userId\":\"u\",\"amount\":499.5,\"currency\":\"USD\"}")).get(0);
        assertEquals(49950L, major.amountMinor());
        FactRow integral = MAPPER.map("pj.payment.payment.captured.v1", "e", Instant.now(), p("{\"userId\":\"u\",\"amount\":49900}")).get(0);
        assertEquals(49900L, integral.amountMinor());
        assertEquals("INR", integral.currency(), "currency defaults to INR");
        assertNull(MAPPER.map("pj.chat.message.sent.v1", "e", Instant.now(), p("{\"senderId\":\"u\",\"amount\":5}")).get(0).amountMinor(),
                "amounts only on payment events");
    }

    @Test
    void profileCompletenessThresholdAndCoarseDimensionsOnly() {
        FactRow done = MAPPER.map("pj.profile.updated.v1", "e1", Instant.now(),
                p("{\"userId\":\"u\",\"completeness\":85,\"community\":\"garhwali\",\"district\":\"PAURI_GARHWAL\",\"displayName\":\"Anjali Rawat\",\"village\":\"Kandai\"}")).get(0);
        assertEquals("PROFILE_COMPLETE", done.eventName());
        assertEquals("GARHWALI", done.community());
        assertEquals("PAURI_GARHWAL", done.district());
        assertEquals("PROFILE_UPDATED", MAPPER.map("pj.profile.updated.v1", "e2", Instant.now(), p("{\"userId\":\"u\",\"completeness\":40}")).get(0).eventName());
        // FactRow has no field that could carry a name or village: PII minimisation by construction
        for (var c : FactRow.class.getRecordComponents()) {
            assertFalse(c.getName().toLowerCase().contains("name") && !c.getName().equals("eventName"), c.getName());
            assertNotEquals("village", c.getName());
        }
    }

    @Test
    void profileViewsAttributeTheViewerAsActorAndTheOwnerAsTarget() {
        FactRow r = MAPPER.map("pj.profile.profile.viewed.v1", "e", Instant.now(), p("{\"viewerId\":\"viewer\",\"profileUserId\":\"owner\"}")).get(0);
        assertEquals(PSEUDO.hash("viewer"), r.userHash());
        assertEquals(PSEUDO.hash("owner"), r.targetHash());
    }

    @Test
    void eventsWithoutAnActorAreKeptAsSystemEvents() {
        FactRow r = MAPPER.map("pj.payment.reconciliation.completed.v1", "e", Instant.now(), p("{\"batch\":7}")).get(0);
        assertEquals("OTHER", r.eventName());
        assertEquals("-", r.userHash());
        assertNull(r.targetHash());
    }
}
