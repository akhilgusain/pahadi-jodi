package in.pahadijodi.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.outbox.OutboxEvent;
import in.pahadijodi.common.outbox.OutboxRepository;
import in.pahadijodi.matchmaking.domain.Repositories.*;
import in.pahadijodi.matchmaking.event.ProfileFacetEventHandler;
import in.pahadijodi.matchmaking.service.DailyRecommendationService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
        "spring.datasource.url=jdbc:h2:mem:matchmaking;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.kafka.consumers.enabled=false", "pj.outbox.relay.enabled=false", "pj.matchmaking.daily.enabled=false"})
class MatchmakingApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProfileFacetEventHandler handler;
    @Autowired DailyRecommendationService dailyService;
    @Autowired ProfileFacetRepository facets;
    @Autowired PartnerPreferenceRepository prefs;
    @Autowired InterestRepository interests;
    @Autowired MatchRecordRepository matchRecords;
    @Autowired ShortlistRepository shortlist;
    @Autowired ProfileNoteRepository notes;
    @Autowired DailyRecommendationRepository dailyRepo;
    @Autowired OutboxRepository outbox;

    // me = Anjali, Garhwali woman from Kandakhal (Pauri Garhwal)
    static final UUID ME = id("me"), AMIT = id("amit"), BHASKAR = id("bhaskar"), CHETAN = id("chetan"), DEEPA = id("deepa"),
            EKLAVYA = id("eklavya"), NOPROFILE = id("noprofile");

    static UUID id(String s) { return UUID.nameUUIDFromBytes(s.getBytes()); }
    RequestPostProcessor as(UUID user) { return jwt().jwt(j -> j.subject(user.toString())); }

    void profile(UUID user, String name, String gender, int age, String community, String district, String division,
                 String village, String gotra, String edu, String tongue) {
        String env = """
            {"eventType":"ProfileUpdated","payload":{"profileId":"%s","userId":"%s","displayName":"%s","gender":"%s","age":%d,
             "community":"%s","caste":"Rajput","gotra":"%s","district":"%s","division":"%s","village":"%s","education":"%s",
             "occupation":"Engineer","motherTongue":"%s","completeness":80,"future":"field"}}""".formatted(
                id("p-" + name), user, name, gender, age, community, gotra, district, division, village, edu, tongue);
        assertThat(handler.handle(env)).isTrue();
    }

    @BeforeEach
    void seed() {
        interests.deleteAll(); matchRecords.deleteAll(); shortlist.deleteAll(); notes.deleteAll(); dailyRepo.deleteAll();
        prefs.deleteAll(); facets.deleteAll(); outbox.deleteAll();
        profile(ME, "Anjali", "FEMALE", 28, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Kandakhal", "Kashyap", "B.Tech", "Garhwali");
        profile(AMIT, "Amit", "MALE", 30, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Kandakhal", "Bharadwaj", "B.Tech", "Garhwali");
        profile(BHASKAR, "Bhaskar", "MALE", 31, "KUMAONI", "ALMORA", "KUMAON", "Jageshwar", "Bhardwaj", "MBA", "Kumaoni");
        profile(CHETAN, "Chetan", "MALE", 29, "GARHWALI", "DEHRADUN", "GARHWAL", "Raipur", "kashyap", "B.Tech", "Garhwali"); // same gotra
        profile(DEEPA, "Deepa", "FEMALE", 27, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Kandakhal", "Joshi", "B.Tech", "Garhwali"); // same gender
        profile(EKLAVYA, "Eklavya", "MALE", 30, "GARHWALI", "DEHRADUN", "GARHWAL", "Raipur", "Negi", "B.Tech", "Garhwali");
    }

    JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    JsonNode fetch(String url, UUID who) throws Exception {
        return body(mvc.perform(get(url).with(as(who))).andExpect(status().isOk()));
    }

    JsonNode fetchList(String name) throws Exception {
        return body(mvc.perform(get("/api/v1/shortlist/lists/{n}", name).with(as(ME))).andExpect(status().isOk()));
    }

    List<String> names(JsonNode items) {
        return java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .map(n -> (n.has("profile") ? n.get("profile") : n).get("displayName").asText()).toList();
    }

    long events(String topic) {
        return outbox.findAll().stream().map(OutboxEvent::getTopic).filter(topic::equals).count();
    }

    // ------------------------------------------------------------------ recommendations

    @Test
    void recommendationsRankByFitAndApplyHardFilters() throws Exception {
        var recs = fetch("/api/v1/matches/recommendations", ME);
        assertThat(names(recs)).containsExactly("Amit", "Eklavya", "Bhaskar");   // not Chetan (gotra) nor Deepa (gender)
        assertThat(recs.get(0).get("score").asDouble()).isGreaterThan(recs.get(2).get("score").asDouble());
        assertThat(recs.get(0).get("reasons").toString()).contains("Both from Kandakhal");
        assertThat(recs.toString()).doesNotContain("userId");
    }

    @Test
    void eklavyaWithKumaonDealbreakerDisappears() throws Exception {
        mvc.perform(put("/api/v1/matches/preferences").with(as(EKLAVYA)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"division\":\"KUMAON\",\"dealbreakers\":[\"DIVISION\"]}")).andExpect(status().isOk());
        assertThat(names(fetch("/api/v1/matches/recommendations", ME))).containsExactly("Amit", "Bhaskar");
        var c = fetch("/api/v1/matches/compatibility/" + id("p-Eklavya"), ME);
        assertThat(c.get("eligible").asBoolean()).isFalse();
        assertThat(c.get("rejectedBecause").asText()).contains("their dealbreaker");
    }

    @Test
    void compatibilityEndpointExplainsScoreAndRejections() throws Exception {
        var ok = fetch("/api/v1/matches/compatibility/" + id("p-Amit"), ME);
        assertThat(ok.get("eligible").asBoolean()).isTrue();
        assertThat(ok.get("algorithmVersion").asText()).isEqualTo("pahadi-v1");
        assertThat(ok.get("match").get("breakdown").size()).isGreaterThanOrEqualTo(7);
        var gotra = fetch("/api/v1/matches/compatibility/" + id("p-Chetan"), ME);
        assertThat(gotra.get("eligible").asBoolean()).isFalse();
        assertThat(gotra.get("rejectedBecause").asText()).contains("gotra");
        mvc.perform(get("/api/v1/matches/compatibility/" + UUID.randomUUID()).with(as(ME))).andExpect(status().isNotFound());
    }

    @Test
    void nearbyStaysInsideScope() throws Exception {
        assertThat(names(fetch("/api/v1/matches/nearby?scope=district", ME))).containsExactly("Amit");
        assertThat(names(fetch("/api/v1/matches/nearby?scope=division", ME))).containsExactly("Amit", "Eklavya");
        mvc.perform(get("/api/v1/matches/nearby?scope=galaxy").with(as(ME))).andExpect(status().isBadRequest());
    }

    @Test
    void membersWithoutProfileAreAskedToCreateOne() throws Exception {
        mvc.perform(get("/api/v1/matches/recommendations").with(as(NOPROFILE))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
        mvc.perform(get("/api/v1/matches/recommendations")).andExpect(status().isUnauthorized());
    }

    @Test
    void preferencesRoundTripAndValidation() throws Exception {
        mvc.perform(put("/api/v1/matches/preferences").with(as(ME)).contentType(MediaType.APPLICATION_JSON).content(
                "{\"ageMin\":28,\"ageMax\":33,\"communities\":[\"GARHWALI\"],\"districts\":[\"PAURI_GARHWAL\",\"DEHRADUN\"],"
                        + "\"motherTongues\":[\"Garhwali\"],\"dealbreakers\":[\"AGE\",\"COMMUNITY\"]}")).andExpect(status().isOk());
        var p = fetch("/api/v1/matches/preferences", ME);
        assertThat(p.get("ageMin").asInt()).isEqualTo(28);
        assertThat(p.get("districts")).hasSize(2);
        assertThat(p.get("dealbreakers")).hasSize(2);
        mvc.perform(put("/api/v1/matches/preferences").with(as(ME)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ageMin\":40,\"ageMax\":30}")).andExpect(status().isUnprocessableEntity());
        // AGE dealbreaker 28-33 keeps Amit(30), Bhaskar(31), Eklavya(30)
        assertThat(names(fetch("/api/v1/matches/recommendations", ME))).containsExactly("Amit", "Eklavya");   // COMMUNITY dealbreaker drops Bhaskar; DISTRICT list is only soft
    }

    // ------------------------------------------------------------------ interests

    JsonNode send(UUID from, String toName, int expect) throws Exception {
        return body(mvc.perform(post("/api/v1/interests").with(as(from)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toProfileId\":\"" + id("p-" + toName) + "\",\"message\":\"Namaste\"}")).andExpect(status().is(expect)));
    }

    @Test
    void sendAcceptCreatesMatchAndEvents() throws Exception {
        var sent = send(ME, "Amit", 201);
        assertThat(sent.get("status").asText()).isEqualTo("SENT");
        send(ME, "Amit", 409);                                                      // duplicate
        assertThat(events("pj.interest.sent.v1")).isEqualTo(1);
        assertThat(names(fetch("/api/v1/matches/recommendations", ME))).doesNotContain("Amit");   // already contacted

        var received = fetch("/api/v1/interests/received", AMIT);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).get("other").get("displayName").asText()).isEqualTo("Anjali");
        String interestId = received.get(0).get("id").asText();
        mvc.perform(post("/api/v1/interests/" + interestId + "/accept").with(as(ME))).andExpect(status().isNotFound());   // sender cannot accept
        mvc.perform(post("/api/v1/interests/" + interestId + "/accept").with(as(AMIT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(post("/api/v1/interests/" + interestId + "/accept").with(as(AMIT))).andExpect(status().isConflict());

        assertThat(events("pj.interest.accepted.v1")).isEqualTo(1);
        assertThat(events("pj.match.created.v1")).isEqualTo(1);
        assertThat(fetch("/api/v1/interests/matches", ME)).hasSize(1);
        assertThat(fetch("/api/v1/interests/matches", AMIT).get(0).get("other").get("displayName").asText()).isEqualTo("Anjali");
        assertThat(fetch("/api/v1/interests/sent", ME).get(0).get("status").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    void mutualInterestMatchesImmediately() throws Exception {
        send(ME, "Bhaskar", 201);
        var back = send(BHASKAR, "Anjali", 201);
        assertThat(back.get("status").asText()).isEqualTo("ACCEPTED");               // completed the other side's interest
        assertThat(matchRecords.count()).isEqualTo(1);
        assertThat(events("pj.match.created.v1")).isEqualTo(1);
    }

    @Test
    void declineStartsNinetyDayCoolOffAndHidesBothSides() throws Exception {
        send(ME, "Amit", 201);
        String id = fetch("/api/v1/interests/received", AMIT).get(0).get("id").asText();
        mvc.perform(post("/api/v1/interests/" + id + "/decline").with(as(AMIT))).andExpect(jsonPath("$.status").value("DECLINED"));
        assertThat(events("pj.interest.declined.v1")).isEqualTo(1);
        send(ME, "Amit", 409);                                                       // cooling off
        assertThat(names(fetch("/api/v1/matches/recommendations", ME))).doesNotContain("Amit");
        assertThat(names(fetch("/api/v1/matches/recommendations", AMIT))).doesNotContain("Anjali");
    }

    @Test
    void withdrawAndStructuralRules() throws Exception {
        var sent = send(ME, "Eklavya", 201);
        mvc.perform(delete("/api/v1/interests/" + sent.get("id").asText()).with(as(ME))).andExpect(jsonPath("$.status").value("WITHDRAWN"));
        assertThat(fetch("/api/v1/interests/received", EKLAVYA)).isEmpty();          // withdrawn is not shown to the recipient
        send(ME, "Eklavya", 201);                                                    // can be re-sent after withdrawal
        send(ME, "Chetan", 422);                                                     // same gotra
        send(ME, "Deepa", 422);                                                      // same gender
        send(ME, "Anjali", 422);                                                     // yourself
        mvc.perform(post("/api/v1/interests").with(as(ME)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toProfileId\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ shortlist

    @Test
    void favoritesNotesAndPrivateLists() throws Exception {
        UUID amit = id("p-Amit");
        mvc.perform(put("/api/v1/shortlist/favorites/" + amit).with(as(ME))).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/shortlist/favorites/" + amit).with(as(ME))).andExpect(status().isNoContent());   // idempotent
        assertThat(names(fetch("/api/v1/shortlist/favorites", ME))).containsExactly("Amit");
        assertThat(fetch("/api/v1/shortlist/favorites", AMIT)).isEmpty();                                          // private to owner

        mvc.perform(put("/api/v1/shortlist/notes/" + amit).with(as(ME)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Parents know his family\"}")).andExpect(jsonPath("$.note").value("Parents know his family"));
        mvc.perform(put("/api/v1/shortlist/notes/" + amit).with(as(ME)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Call on Sunday\"}")).andExpect(status().isOk());
        assertThat(fetch("/api/v1/shortlist/notes", ME)).hasSize(1);
        assertThat(fetch("/api/v1/shortlist/notes", AMIT)).isEmpty();
        mvc.perform(delete("/api/v1/shortlist/notes/" + amit).with(as(ME))).andExpect(status().isNoContent());
        assertThat(fetch("/api/v1/shortlist/notes", ME)).isEmpty();

        mvc.perform(put("/api/v1/shortlist/lists/{n}/{id}", "Family picks", amit).with(as(ME))).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/shortlist/lists/{n}/{id}", "Family picks", id("p-Bhaskar")).with(as(ME))).andExpect(status().isNoContent());
        assertThat(fetch("/api/v1/shortlist/lists", ME)).hasSize(1);
        assertThat(fetchList("Family picks")).hasSize(2);
        mvc.perform(delete("/api/v1/shortlist/lists/{n}/{id}", "Family picks", amit).with(as(ME))).andExpect(status().isNoContent());
        assertThat(fetchList("Family picks")).hasSize(1);

        mvc.perform(delete("/api/v1/shortlist/favorites/" + amit).with(as(ME))).andExpect(status().isNoContent());
        assertThat(fetch("/api/v1/shortlist/favorites", ME)).isEmpty();
        mvc.perform(put("/api/v1/shortlist/favorites/" + id("p-Anjali")).with(as(ME))).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/shortlist/enemies").with(as(ME))).andExpect(status().isNotFound());
    }

    @Test
    void blockHidesBothWaysDropsFavoriteAndStopsInterests() throws Exception {
        UUID amit = id("p-Amit");
        mvc.perform(put("/api/v1/shortlist/favorites/" + amit).with(as(ME))).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/shortlist/blocked/" + amit).with(as(ME))).andExpect(status().isNoContent());
        assertThat(fetch("/api/v1/shortlist/favorites", ME)).isEmpty();
        assertThat(names(fetch("/api/v1/matches/recommendations", ME))).doesNotContain("Amit");
        assertThat(names(fetch("/api/v1/matches/recommendations", AMIT))).doesNotContain("Anjali");   // blocked members vanish for the blocker too
        send(ME, "Amit", 403);
        send(AMIT, "Anjali", 403);
    }

    @Test
    void ignoreHidesFromRecommendationsOnly() throws Exception {
        mvc.perform(put("/api/v1/shortlist/ignored/" + id("p-Bhaskar")).with(as(ME))).andExpect(status().isNoContent());
        assertThat(names(fetch("/api/v1/matches/recommendations", ME))).doesNotContain("Bhaskar");
        assertThat(names(fetch("/api/v1/shortlist/ignored", ME))).containsExactly("Bhaskar");
    }

    // ------------------------------------------------------------------ daily batch & events

    @Test
    void dailyBatchIsIdempotentAndPublishesReadyEvent() throws Exception {
        var today = fetch("/api/v1/matches/daily", ME);                     // generated on demand
        assertThat(names(today)).containsExactly("Amit", "Eklavya", "Bhaskar");
        assertThat(today.get(0).get("rank").asInt()).isEqualTo(1);
        int rows = (int) dailyRepo.count();

        int users = dailyService.runAll(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));   // nightly job, same date
        assertThat(users).isEqualTo(6);
        assertThat(dailyRepo.findByUserIdAndRecDateOrderByRankNo(ME, LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")))).hasSize(3);
        assertThat(dailyRepo.count()).isGreaterThanOrEqualTo(rows);
        dailyService.runAll(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
        assertThat(dailyRepo.findByUserIdAndRecDateOrderByRankNo(ME, LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")))).hasSize(3);   // no duplicates
        assertThat(events("pj.match.daily-recommendations-ready.v1")).isGreaterThanOrEqualTo(6);
    }

    @Test
    void facetHandlerUpsertsAndSkipsGarbage() {
        profile(AMIT, "Amit", "MALE", 31, "GARHWALI", "TEHRI_GARHWAL", "GARHWAL", "Chamba", "Bharadwaj", "M.Tech", "Garhwali");
        assertThat(facets.findByUserId(AMIT).orElseThrow().getDistrict()).isEqualTo("TEHRI_GARHWAL");
        assertThat(facets.count()).isEqualTo(6);
        assertThat(handler.handle("nope")).isFalse();
        assertThat(handler.handle("{\"payload\":{\"displayName\":\"x\"}}")).isFalse();
    }
}
