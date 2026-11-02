package in.pahadijodi.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.search.event.ProfileEventHandler;
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
        "spring.datasource.url=jdbc:h2:mem:search;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.kafka.consumers.enabled=false", "pj.outbox.relay.enabled=false",
        "pj.search.dev-endpoints=true"})
class SearchApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProfileEventHandler handler;
    @Autowired in.pahadijodi.search.adapter.InMemorySearchAdapter fakeIndex;

    static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    RequestPostProcessor me() { return jwt().jwt(j -> j.subject(ME.toString())); }
    RequestPostProcessor other() { return jwt().jwt(j -> j.subject(OTHER.toString())); }

    String doc(UUID user, String name, int age, String community, String district, String division, String village,
               String caste, String occupation, int completeness) {
        return """
            {"profileId":"%s","userId":"%s","displayName":"%s","gender":"FEMALE","age":%d,"community":"%s",
             "caste":"%s","district":"%s","division":"%s","village":"%s","education":"B.Tech","occupation":"%s",
             "motherTongue":"Garhwali","completeness":%d,"somethingNew":"ignored"}"""
                .formatted(UUID.nameUUIDFromBytes(name.getBytes()), user, name, age, community, caste, district, division,
                        village, occupation, completeness);
    }

    void index(String body) throws Exception {
        mvc.perform(put("/api/v1/search/dev/index").with(me()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
    }

    @BeforeEach
    void seed() throws Exception {
        fakeIndex.clear();
        index(doc(UUID.randomUUID(), "Anjali Rawat", 28, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Kandakhal", "Rajput", "Software Engineer", 90));
        index(doc(UUID.randomUUID(), "Meena Joshi", 31, "KUMAONI", "ALMORA", "KUMAON", "Jageshwar", "Brahmin", "Teacher", 70));
        index(doc(UUID.randomUUID(), "Pooja Negi", 25, "GARHWALI", "DEHRADUN", "GARHWAL", "Raipur", "Rajput", "Doctor", 80));
        index(doc(ME, "My Own Profile", 29, "GARHWALI", "DEHRADUN", "GARHWAL", "Raipur", "Rajput", "Doctor", 100));
    }

    JsonNode fetch(String url, RequestPostProcessor who) throws Exception {
        return json.readTree(mvc.perform(get(url).with(who)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/search/profiles")).andExpect(status().isUnauthorized());
    }

    @Test
    void filtersExcludeOwnProfileAndNeverLeakUserIds() throws Exception {
        var all = fetch("/api/v1/search/profiles?division=GARHWAL", me());
        assertThat(all.get("total").asInt()).isEqualTo(2);                         // own profile excluded
        assertThat(all.toString()).doesNotContain("userId").doesNotContain("My Own Profile");
        assertThat(fetch("/api/v1/search/profiles?division=GARHWAL", other()).get("total").asInt()).isEqualTo(3);

        assertThat(fetch("/api/v1/search/profiles?ageMin=26&ageMax=30", me()).get("total").asInt()).isEqualTo(1);
        assertThat(fetch("/api/v1/search/profiles?community=KUMAONI", me()).get("items").get(0).get("village").asText()).isEqualTo("Jageshwar");
        assertThat(fetch("/api/v1/search/profiles?community=KUMAONI&community=GARHWALI", me()).get("total").asInt()).isEqualTo(3);
        assertThat(fetch("/api/v1/search/profiles?village=kanda", me()).get("total").asInt()).isEqualTo(1);
        assertThat(fetch("/api/v1/search/profiles?caste=rajput&occupation=engineer", me()).get("total").asInt()).isEqualTo(1);
    }

    @Test
    void freeTextAndSorting() throws Exception {
        var r = fetch("/api/v1/search/profiles?q=pooja", me());
        assertThat(r.get("total").asInt()).isEqualTo(1);
        assertThat(r.get("items").get(0).get("displayName").asText()).isEqualTo("Pooja Negi");
        var byCompleteness = fetch("/api/v1/search/profiles?sort=COMPLETENESS", me());
        assertThat(byCompleteness.get("items").get(0).get("completeness").asInt()).isEqualTo(90);
    }

    @Test
    void cursorPaginationWalksAllResults() throws Exception {
        var p1 = fetch("/api/v1/search/profiles?size=2&sort=COMPLETENESS", me());
        assertThat(p1.get("items")).hasSize(2);
        String cursor = p1.get("nextCursor").asText();
        var p2 = fetch("/api/v1/search/profiles?size=2&sort=COMPLETENESS&cursor=" + cursor, me());
        assertThat(p2.get("items")).hasSize(1);
        assertThat(p2.get("nextCursor").isNull()).isTrue();
        mvc.perform(get("/api/v1/search/profiles?cursor=garbage").with(me())).andExpect(status().isBadRequest());
    }

    @Test
    void villageSuggest() throws Exception {
        var r = fetch("/api/v1/search/villages/suggest?q=kan", me());
        assertThat(r.get(0).get("name").asText()).isEqualTo("Kandakhal");
        assertThat(fetch("/api/v1/search/villages/suggest?q=Khirshu", me()).get(0).get("name").asText()).isEqualTo("Khirsu");  // alt spelling
        assertThat(fetch("/api/v1/search/villages/suggest?q=जाग", me()).get(0).get("name").asText()).isEqualTo("Jageshwar");  // Hindi
    }

    @Test
    void savedSearchCrudOwnershipAndRun() throws Exception {
        String body = "{\"name\":\"Garhwal engineers\",\"criteria\":{\"division\":\"GARHWAL\",\"occupation\":\"engineer\"}}";
        var created = json.readTree(mvc.perform(post("/api/v1/search/saved").with(me()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String id = created.get("id").asText();
        assertThat(fetch("/api/v1/search/saved", me())).hasSize(1);
        assertThat(fetch("/api/v1/search/saved/" + id + "/run", me()).get("total").asInt()).isEqualTo(1);
        mvc.perform(get("/api/v1/search/saved/" + id).with(other())).andExpect(status().isNotFound());   // not yours
        mvc.perform(put("/api/v1/search/saved/" + id).with(me()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\",\"criteria\":{\"division\":\"KUMAON\"}}")).andExpect(jsonPath("$.name").value("Renamed"));
        mvc.perform(delete("/api/v1/search/saved/" + id).with(me())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/search/saved/" + id).with(me())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/search/saved").with(me()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void kafkaHandlerIndexesEnvelopeAndSkipsGarbage() throws Exception {
        UUID pid = UUID.randomUUID();
        String env = "{\"eventType\":\"ProfileUpdated\",\"payload\":{\"profileId\":\"" + pid + "\",\"userId\":\"" + UUID.randomUUID()
                + "\",\"displayName\":\"Kavita Bisht\",\"gender\":\"FEMALE\",\"age\":27,\"community\":\"KUMAONI\",\"district\":\"NAINITAL\","
                + "\"division\":\"KUMAON\",\"completeness\":60,\"unknown\":1}}";
        assertThat(handler.handle(env)).isTrue();
        assertThat(fetch("/api/v1/search/profiles?q=kavita", me()).get("total").asInt()).isEqualTo(1);
        assertThat(handler.handle("not json")).isFalse();
        assertThat(handler.handle("{\"payload\":{}}")).isFalse();
    }
}
