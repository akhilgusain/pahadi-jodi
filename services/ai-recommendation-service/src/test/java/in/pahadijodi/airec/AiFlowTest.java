package in.pahadijodi.airec;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.airec.domain.ProfileProjectionRepository;
import in.pahadijodi.airec.llm.FakeLlm;
import in.pahadijodi.airec.service.ProfileEventsConsumer;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:airectest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false", "pj.kafka.consumers.enabled=false", "pj.ai.daily-quota=100"})
class AiFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProfileEventsConsumer consumer;
    @Autowired ProfileProjectionRepository projections;
    @Autowired FakeLlm fake;

    // me: Garhwali woman, Pauri Garhwal.  a: same district.  b: Kumaoni man from Almora.  c: older Garhwali man, Dehradun.  d: another woman.
    static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000a1"), ME_P = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a2"), A_P = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000a3"), B_P = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
    static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000a4"), C_P = UUID.fromString("00000000-0000-0000-0000-0000000000b4");
    static final UUID D = UUID.fromString("00000000-0000-0000-0000-0000000000a5"), D_P = UUID.fromString("00000000-0000-0000-0000-0000000000b5");

    static RequestPostProcessor as(UUID u) { return jwt().jwt(j -> j.subject(u.toString())); }

    void event(UUID pid, UUID uid, String name, String gender, int age, String community, String district, String division,
               String lang, String edu, String occ, String gotra, String village) {
        consumer.onProfileUpdated("""
            {"eventId":"%s","eventType":"ProfileUpdated","payload":{"profileId":"%s","userId":"%s","displayName":"%s","gender":"%s","age":%d,
             "community":"%s","district":"%s","division":"%s","motherTongue":"%s","education":%s,"occupation":%s,"gotra":%s,"village":%s,"completeness":%d}}"""
            .formatted(UUID.randomUUID(), pid, uid, name, gender, age, community, district, division, lang,
                    q(edu), q(occ), q(gotra), q(village), edu == null ? 60 : 92));
    }

    static String q(String s) { return s == null ? "null" : "\"" + s + "\""; }

    @BeforeEach
    void profiles() {
        event(ME_P, ME, "Anjali", "FEMALE", 28, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Garhwali", "B.Tech", "Software Engineer", "Kashyap", "Kandai");
        event(A_P, A, "Rohit", "MALE", 30, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Garhwali", "B.Tech", "Civil Engineer", "Kashyap", "Srinagar");
        event(B_P, B, "Deepak", "MALE", 29, "KUMAONI", "ALMORA", "KUMAON", "Kumaoni", "MBA", "Banker", "Bharadwaj", "Dwarahat");
        event(C_P, C, "Mohan", "MALE", 41, "GARHWALI", "DEHRADUN", "GARHWAL", "Garhwali", null, null, null, null);
        event(D_P, D, "Neha", "FEMALE", 27, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Garhwali", "B.Sc", "Teacher", "Joshi", "Pauri");
    }

    @AfterEach
    void healProvider() { fake.setFailing(false); }

    JsonNode call(String path, UUID user, String body) throws Exception {
        var r = mvc.perform(post(path).with(as(user)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void authAndProfileAreRequired() throws Exception {
        mvc.perform(post("/api/v1/ai/safety/check").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hi\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ai/profile/review").with(as(UUID.randomUUID()))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
    }

    @Test
    void profileEventsBuildProjectionAndEmbedOnlyWhenFeaturesChange() {
        var before = projections.findById(ME_P).orElseThrow();
        assertNotNull(before.getEmbedding());
        assertEquals(64, before.getEmbedding().split(",").length);
        String hash = before.getTextHash();
        event(ME_P, ME, "Anjali R", "FEMALE", 28, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Garhwali", "B.Tech", "Software Engineer", "Kashyap", "Kandai");   // name change only
        assertEquals(hash, projections.findById(ME_P).orElseThrow().getTextHash());
        event(ME_P, ME, "Anjali", "FEMALE", 28, "GARHWALI", "DEHRADUN", "GARHWAL", "Garhwali", "B.Tech", "Software Engineer", "Kashyap", "Kandai");        // moved district
        assertNotEquals(hash, projections.findById(ME_P).orElseThrow().getTextHash());
        consumer.onProfileUpdated("garbage");                                 // must not throw
        consumer.onProfileUpdated("{\"payload\":{\"profileId\":\"nope\"}}");  // must not throw
    }

    @Test
    void similarProfilesAreRankedByEmbeddingAndFilteredByGender() throws Exception {
        var res = mvc.perform(get("/api/v1/ai/recommendations/similar").with(as(ME))).andExpect(status().isOk()).andReturn();
        JsonNode list = json.readTree(res.getResponse().getContentAsString());
        assertTrue(list.size() >= 3);
        for (JsonNode n : list) assertNotEquals(ME_P.toString(), n.get("profileId").asText());
        assertEquals(A_P.toString(), list.get(0).get("profileId").asText(), "same district + community + language + education should rank first");
        assertTrue(json.convertValue(list.get(0).get("reasons"), java.util.List.class).contains("SAME_DISTRICT"));
        double top = list.get(0).get("similarity").asDouble();
        for (JsonNode n : list) {
            assertEquals("MALE", projections.findById(UUID.fromString(n.get("profileId").asText())).orElseThrow().getGender());
            assertTrue(n.get("similarity").asDouble() <= top);
        }
        // explicit ANY includes the other woman, and limit applies
        mvc.perform(get("/api/v1/ai/recommendations/similar").with(as(ME)).param("gender", "ANY").param("limit", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/ai/recommendations/similar").with(as(ME)).param("limit", "500")).andExpect(status().is4xxClientError());
    }

    @Test
    void bioSuggestionsAreThreeDistinctPiiFreeVariantsInEnglishAndHindi() throws Exception {
        JsonNode en = call("/api/v1/ai/profile/bio-suggestions", ME,
                "{\"tone\":\"warm\",\"maxWords\":60,\"interests\":[\"trekking\",\"call me 9876543210\"]}");
        assertEquals("llm", en.get("source").asText());
        assertEquals(3, en.get("suggestions").size());
        java.util.Set<String> distinct = new java.util.HashSet<>();
        for (JsonNode s : en.get("suggestions")) {
            String t = s.get("text").asText();
            distinct.add(t);
            assertTrue(t.split("\\s+").length <= 60, t);
            assertFalse(t.contains("9876543210"), "phone number leaked: " + t);
        }
        assertEquals(3, distinct.size());
        assertTrue(distinct.stream().anyMatch(t -> t.contains("Pauri Garhwal")), "at least one variant should name the district");
        assertFalse(en.toString().contains("[PHONE]"), "redaction placeholders must never reach the user");

        JsonNode hi = call("/api/v1/ai/profile/bio-suggestions", ME, "{\"language\":\"hi\"}");
        assertTrue(hi.get("suggestions").get(0).get("text").asText().matches("(?s).*[\\u0900-\\u097F].*"));
        assertTrue(hi.get("suggestions").get(0).get("text").asText().contains("पौड़ी गढ़वाल"));
    }

    @Test
    void identicalRequestsAreServedFromTheContentHashCache() throws Exception {
        String body = "{\"tone\":\"formal\",\"maxWords\":80,\"draft\":\"cache probe " + UUID.randomUUID() + "\"}";
        assertFalse(call("/api/v1/ai/profile/bio-suggestions", ME, body).get("cached").asBoolean());
        assertTrue(call("/api/v1/ai/profile/bio-suggestions", ME, body).get("cached").asBoolean());
    }

    @Test
    void providerOutageFallsBackToTemplatesForEveryFeature() throws Exception {
        fake.setFailing(true);
        JsonNode bio = call("/api/v1/ai/profile/bio-suggestions", ME, "{\"draft\":\"outage probe " + UUID.randomUUID() + "\"}");
        assertEquals("fallback", bio.get("source").asText());
        assertTrue(bio.get("suggestions").size() >= 1);

        JsonNode ice = call("/api/v1/ai/chat/ice-breakers", ME, "{\"profileId\":\"" + B_P + "\",\"tone\":\"formal\"}");
        assertEquals("fallback", ice.get("source").asText());
        assertTrue(ice.get("suggestions").size() >= 1);

        JsonNode compat = call("/api/v1/ai/compatibility/explain", ME, "{\"profileId\":\"" + A_P + "\"}");
        assertEquals("fallback", compat.get("source").asText());
        assertEquals(100, compat.get("overall").asInt());      // numeric score does not depend on the LLM
        assertFalse(compat.get("summary").asText().isBlank());
    }

    @Test
    void iceBreakersAreGroundedInWhatIsShared() throws Exception {
        JsonNode same = call("/api/v1/ai/chat/ice-breakers", ME, "{\"profileId\":\"" + A_P + "\",\"count\":4}");
        assertEquals("llm", same.get("source").asText());
        assertTrue(json.convertValue(same.get("shared"), java.util.List.class).containsAll(java.util.List.of("DISTRICT", "LANGUAGE", "COMMUNITY")));
        assertTrue(same.get("suggestions").get(0).asText().contains("Pauri Garhwal"), same.toString());
        for (JsonNode s : same.get("suggestions")) {
            assertTrue(s.asText().split("\\s+").length <= 25, s.asText());
            assertFalse(s.asText().toLowerCase().matches(".*(whatsapp|number|photo|salary|horoscope|kundli).*"), s.asText());
        }
        assertTrue(same.get("suggestions").size() <= 4);

        // Garhwal x Kumaon: no shared district, but festivals observed in both regions are fair game
        JsonNode cross = call("/api/v1/ai/chat/ice-breakers", ME, "{\"profileId\":\"" + B_P + "\"}");
        assertTrue(json.convertValue(cross.get("shared"), java.util.List.class).contains("FESTIVALS"));
        assertFalse(json.convertValue(cross.get("shared"), java.util.List.class).contains("DISTRICT"));
        assertTrue(cross.toString().matches(".*(Harela|Phool Dei|Nanda Devi).*"), cross.toString());

        JsonNode hi = call("/api/v1/ai/chat/ice-breakers", ME, "{\"profileId\":\"" + A_P + "\",\"language\":\"hi\"}");
        assertTrue(hi.get("suggestions").get(0).asText().matches("(?s).*[\\u0900-\\u097F].*"), hi.toString());

        mvc.perform(post("/api/v1/ai/chat/ice-breakers").with(as(ME)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileId\":\"" + ME_P + "\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/ai/chat/ice-breakers").with(as(ME)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileId\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isNotFound());
    }

    @Test
    void compatibilityHasDeterministicBreakdownAndBalancedNarrative() throws Exception {
        JsonNode same = call("/api/v1/ai/compatibility/explain", ME, "{\"profileId\":\"" + A_P + "\"}");
        assertEquals(100, same.get("overall").asInt());
        assertEquals(100, same.get("breakdown").get("region").asInt());
        assertTrue(same.get("considerations").toString().toLowerCase().contains("gotra"), "same gotra should be flagged for discussion");
        assertTrue(same.get("disclaimer").asText().toLowerCase().contains("not a prediction"));

        JsonNode cross = call("/api/v1/ai/compatibility/explain", ME, "{\"profileId\":\"" + B_P + "\"}");
        // region 30*.30 + community 40*.20 + language 50*.20 + education 80*.15 + age 100*.15 = 54
        assertEquals(30, cross.get("breakdown").get("region").asInt());
        assertEquals(54, cross.get("overall").asInt());
        assertTrue(cross.get("summary").asText().contains("54"));
        assertTrue(cross.get("strengths").size() <= 3 && cross.get("considerations").size() >= 1);
        String all = cross.toString().toLowerCase();
        assertFalse(all.contains("guarantee") || all.contains("perfect match") || all.contains("caste"), all);
        // facts about the other person that must stay out of the response
        assertFalse(all.contains("deepak") || all.contains("dwarahat"), all);
    }

    @Test
    void profileReviewListsWhatIsMissing() throws Exception {
        JsonNode thin = call("/api/v1/ai/profile/review", C, "");
        assertEquals("NEEDS_WORK", thin.get("level").asText());
        String hints = thin.get("hints").toString();
        assertTrue(hints.contains("village") && hints.contains("education") && hints.contains("occupation") && hints.contains("gotra"), hints);
        JsonNode good = call("/api/v1/ai/profile/review", ME, "");
        assertEquals("STRONG", good.get("level").asText());
    }

    @Test
    void safetyRulesAllowBlockAndWarn() throws Exception {
        assertEquals("ALLOW", call("/api/v1/ai/safety/check", ME, "{\"text\":\"Namaste, I liked your profile. Are you from Pauri?\"}").get("action").asText());

        JsonNode threat = call("/api/v1/ai/safety/check", ME, "{\"text\":\"Why are you ignoring me? I know where you live, reply now\"}");
        assertEquals("BLOCK", threat.get("action").asText());
        assertTrue(threat.get("labels").toString().contains("HARASSMENT"));

        JsonNode scam = call("/api/v1/ai/safety/check", ME, "{\"text\":\"Send money for customs fees, guaranteed returns on crypto investment, click here\"}");
        assertEquals("BLOCK", scam.get("action").asText());
        assertTrue(scam.get("labels").toString().contains("SCAM"));

        JsonNode contact = call("/api/v1/ai/safety/check", ME, "{\"text\":\"Message me on whatsapp 9876543210\"}");
        assertEquals("WARN", contact.get("action").asText());
        assertTrue(contact.get("labels").toString().contains("CONTACT_SHARING"));

        JsonNode rude = call("/api/v1/ai/safety/check", ME, "{\"text\":\"you are a stupid idiot\"}");
        assertTrue(rude.get("labels").toString().contains("TOXICITY"));

        mvc.perform(post("/api/v1/ai/safety/check").with(as(ME)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + "x".repeat(2001) + "\"}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void festivalsFilterByDivision() throws Exception {
        mvc.perform(get("/api/v1/ai/culture/festivals").with(as(ME)).param("division", "KUMAON"))
                .andExpect(jsonPath("$[?(@.name=='Bikhauti')]").exists())
                .andExpect(jsonPath("$[?(@.name=='Egas')]").doesNotExist());
        mvc.perform(get("/api/v1/ai/culture/festivals").with(as(ME)).param("division", "GARHWAL"))
                .andExpect(jsonPath("$[?(@.name=='Egas')]").exists());
    }
}
