package in.pahadijodi.horoscope;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import in.pahadijodi.common.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:horoscope;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false", "pj.kafka.consumers.enabled=false",
        "pj.horoscope.data-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})   // 32 bytes: AES key, so the encrypted path is exercised
class HoroscopeApiTest {
    @Autowired MockMvc mvc;
    @Autowired OutboxRepository outbox;
    @Autowired javax.sql.DataSource dataSource;

    static final String ANJALI = "0b8f4c0e-6a54-4b52-9f0a-1d1d6d6f7a01", ROHIT = "0b8f4c0e-6a54-4b52-9f0a-1d1d6d6f7a02",
            GHOST = "0b8f4c0e-6a54-4b52-9f0a-1d1d6d6f7a03", HIDER = "0b8f4c0e-6a54-4b52-9f0a-1d1d6d6f7a04";

    static RequestPostProcessor as(String id) { return jwt().jwt(j -> j.subject(id)); }

    static String birth(String date, String time, String place, String visibility) {
        return "{\"birthDate\":\"" + date + "\"," + (time == null ? "" : "\"birthTime\":\"" + time + "\",")
                + "\"placeId\":\"" + place + "\"" + (visibility == null ? "" : ",\"visibility\":\"" + visibility + "\"") + "}";
    }

    @Test
    void unauthenticatedRejected_butStatelessEndpointNeedsAuthToo() throws Exception {
        mvc.perform(get("/api/v1/horoscope/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/horoscope/match").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void fullFlow_save_read_match_privacy_erase() throws Exception {
        mvc.perform(get("/api/v1/horoscope/me").with(as(ANJALI))).andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/horoscope/me").with(as(ANJALI)).contentType(MediaType.APPLICATION_JSON)
                        .content(birth("1996-04-12", "06:30", "dehradun", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moonRashi.number").isNumber())
                .andExpect(jsonPath("$.nakshatra.name").isString())
                .andExpect(jsonPath("$.pada").isNumber())
                .andExpect(jsonPath("$.gana").isString())
                .andExpect(jsonPath("$.nadi").isString())
                .andExpect(jsonPath("$.lagnaRashi.number").isNumber())
                .andExpect(jsonPath("$.manglik.level").isString())
                .andExpect(jsonPath("$.lowConfidence").value(false));

        // owner view echoes the birth input (decrypted from the AES-GCM column)
        mvc.perform(get("/api/v1/horoscope/me").with(as(ANJALI))).andExpect(status().isOk())
                .andExpect(jsonPath("$.birth.birthDate").value("1996-04-12"))
                .andExpect(jsonPath("$.birth.birthTime").value("06:30"))
                .andExpect(jsonPath("$.birth.place.name").value("Dehradun"))
                .andExpect(jsonPath("$.birth.visibility").value("MATCHES_ONLY"));

        // the stored payload really is ciphertext
        try (var c = dataSource.getConnection(); var rs = c.createStatement().executeQuery("select enc_payload from birth_profile")) {
            assertTrue(rs.next());
            assertTrue(rs.getString(1).startsWith("enc1:"), "birth data must be encrypted at rest");
            assertFalse(rs.getString(1).contains("1996"));
        }

        // other member has no data yet -> 409
        mvc.perform(get("/api/v1/horoscope/match/" + ROHIT).with(as(ANJALI))).andExpect(status().isConflict());

        mvc.perform(put("/api/v1/horoscope/me").with(as(ROHIT)).contentType(MediaType.APPLICATION_JSON)
                        .content(birth("1993-11-03", null, "almora", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lowConfidence").value(true))
                .andExpect(jsonPath("$.lagnaRashi").doesNotExist());

        mvc.perform(get("/api/v1/horoscope/match/" + ROHIT).with(as(ANJALI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scores.length()").value(8))
                .andExpect(jsonPath("$.max").value(36.0))
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.band").isString())
                .andExpect(jsonPath("$.myRole").value("BOY"))
                .andExpect(jsonPath("$.manglik.status").isString())
                .andExpect(jsonPath("$.disclaimer").isString());
        mvc.perform(get("/api/v1/horoscope/match/" + ROHIT + "?role=GIRL").with(as(ANJALI)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.myRole").value("GIRL"));
        mvc.perform(get("/api/v1/horoscope/match/" + ROHIT + "?role=MAYBE").with(as(ANJALI))).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/horoscope/match/" + ANJALI).with(as(ANJALI))).andExpect(status().isUnprocessableEntity());

        // privacy: a HIDDEN member cannot be matched, and cannot match others
        mvc.perform(put("/api/v1/horoscope/me").with(as(HIDER)).contentType(MediaType.APPLICATION_JSON)
                .content(birth("1990-02-02", "10:00", "nainital", "HIDDEN"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/horoscope/match/" + HIDER).with(as(ANJALI))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/horoscope/match/" + ANJALI).with(as(HIDER))).andExpect(status().isForbidden());

        // event published without birth details
        var events = outbox.findAll().stream().filter(e -> e.getTopic().equals("pj.horoscope.updated.v1")).toList();
        assertFalse(events.isEmpty());
        events.forEach(e -> {
            assertFalse(e.getEnvelope().contains("1996-04-12"));
            assertFalse(e.getEnvelope().contains("06:30"));
            assertFalse(e.getEnvelope().toLowerCase().contains("dehradun"));
            assertTrue(e.getEnvelope().contains("moonNakshatra"));
        });

        // erasure
        mvc.perform(delete("/api/v1/horoscope/me").with(as(ROHIT))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/horoscope/me").with(as(ROHIT))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/horoscope/match/" + ROHIT).with(as(ANJALI))).andExpect(status().isConflict());
    }

    @Test
    void sameBirthDataSharesOneImmutableChart() throws Exception {
        mvc.perform(put("/api/v1/horoscope/me").with(as(GHOST)).contentType(MediaType.APPLICATION_JSON)
                .content(birth("2000-06-15", "12:00", "haridwar", null))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/horoscope/me").with(as(GHOST)).contentType(MediaType.APPLICATION_JSON)
                .content(birth("2000-06-15", "12:00", "haridwar", null))).andExpect(status().isOk());
        try (var c = dataSource.getConnection(); var rs = c.createStatement().executeQuery(
                "select count(*) from kundli k join user_kundli u on u.kundli_id = k.id where u.user_id = '" + GHOST + "'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void validation() throws Exception {
        var bad = put("/api/v1/horoscope/me").with(as(ANJALI)).contentType(MediaType.APPLICATION_JSON);
        mvc.perform(bad.content(birth("2999-01-01", "10:00", "dehradun", null))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_BIRTH_DATE"));
        mvc.perform(put("/api/v1/horoscope/me").with(as(ANJALI)).contentType(MediaType.APPLICATION_JSON)
                .content(birth("1990-01-01", "10:00", "atlantis", null))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PLACE"));
        mvc.perform(put("/api/v1/horoscope/me").with(as(ANJALI)).contentType(MediaType.APPLICATION_JSON)
                .content(birth("1990-01-01", "25:99", "dehradun", null))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void statelessMatchAndPlaces() throws Exception {
        // boy Pushya-2 and girl Pushya-3: same nakshatra, different pada -> Nadi cancelled, 36/36
        mvc.perform(post("/api/v1/horoscope/match").with(as(ANJALI)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boy\":{\"nakshatra\":8,\"pada\":2},\"girl\":{\"nakshatra\":8,\"pada\":3}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(36.0))
                .andExpect(jsonPath("$.band").value("EXCELLENT"))
                .andExpect(jsonPath("$.manglik.status").value("NEITHER"));
        // Manglik mismatch is surfaced as a flag even when the total is high
        mvc.perform(post("/api/v1/horoscope/match").with(as(ANJALI)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boy\":{\"nakshatra\":8,\"pada\":2,\"manglik\":\"FULL\"},\"girl\":{\"nakshatra\":8,\"pada\":3}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.doshaFlags[0]").value("MANGLIK_MISMATCH"))
                .andExpect(jsonPath("$.manglik.warning").isString());
        // inconsistent rashi vs nakshatra/pada
        mvc.perform(post("/api/v1/horoscope/match").with(as(ANJALI)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"boy\":{\"nakshatra\":8,\"pada\":2,\"rashi\":1},\"girl\":{\"nakshatra\":8,\"pada\":3}}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/horoscope/match").with(as(ANJALI)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"boy\":{\"nakshatra\":99,\"pada\":2},\"girl\":{\"nakshatra\":8,\"pada\":3}}")).andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/v1/horoscope/places?q=pith").with(as(ANJALI))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pithoragarh"));
        mvc.perform(get("/api/v1/horoscope/places").with(as(ANJALI))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
    }
}
