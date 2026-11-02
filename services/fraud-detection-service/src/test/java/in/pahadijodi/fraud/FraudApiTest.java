package in.pahadijodi.fraud;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.outbox.OutboxRepository;
import in.pahadijodi.fraud.messaging.FraudEventListener;
import java.util.UUID;
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
        "spring.datasource.url=jdbc:h2:mem:fraudtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false", "pj.kafka.consumers.enabled=false"})
class FraudApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxRepository outbox;
    @Autowired FraudEventListener listener;

    static RequestPostProcessor user(UUID id) { return jwt().jwt(j -> j.subject(id.toString())); }
    static RequestPostProcessor admin(UUID id) {
        return jwt().jwt(j -> j.subject(id.toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private JsonNode signal(UUID as, String body) throws Exception {
        var res = mvc.perform(post("/api/v1/fraud/signals").with(user(as)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/fraud/signals").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"LOGIN\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sharedIpDeviceAndDisposableEmailEscalateToShadowLimitAndOpenACase() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        String net = "\"ip\":\"203.0.113.77\",\"deviceHash\":\"dev-farm-1\"";
        assertEquals("ALLOW", signal(a, "{\"type\":\"REGISTERED\"," + net + ",\"emailDomain\":\"gmail.com\"}").get("action").asText());
        signal(b, "{\"type\":\"REGISTERED\"," + net + ",\"emailDomain\":\"gmail.com\"}");
        var third = signal(c, "{\"type\":\"REGISTERED\"," + net + ",\"emailDomain\":\"mailinator.com\"}");

        assertEquals(75, third.get("score").asInt());          // ip 25 + device 30 + disposable 20
        assertEquals("SHADOW_LIMIT", third.get("action").asText());
        assertThat(third.get("reasons").findValuesAsText("code"), containsInAnyOrder("IP_VELOCITY", "DEVICE_MULTI_ACCOUNT", "DISPOSABLE_EMAIL"));

        var topics = outbox.findAll().stream().map(e -> e.getTopic() + "|" + e.getPartitionKey()).toList();
        assertThat(topics, hasItem("pj.fraud.risk-scored.v1|" + c));
        assertThat(topics, hasItem("pj.fraud.action.v1|" + c));

        mvc.perform(get("/api/v1/fraud/admin/cases").with(admin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId=='" + c + "')].action", contains("SHADOW_LIMIT")));
    }

    @Test
    void plainUsersCannotReadRiskOrCasesOrReportForOthers() throws Exception {
        UUID u = UUID.randomUUID();
        mvc.perform(get("/api/v1/fraud/admin/cases").with(user(u))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/fraud/users/" + u + "/risk").with(user(u))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/fraud/signals").with(user(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"type\":\"LOGIN\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/fraud/signals").with(user(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"NOT_A_TYPE\"}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void duplicateProfileTextAndDuplicatePhotoAreDetectedAcrossAccounts() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        String text = "Software engineer from Almora who loves trekking and Kumaoni folk songs and wants a caring family oriented partner";
        signal(a, "{\"type\":\"PROFILE_UPDATED\",\"profileText\":\"" + text + "\",\"photoHash\":\"a5a5a5a5a5a5a5a5\"}");
        var r = signal(b, "{\"type\":\"PROFILE_UPDATED\",\"profileText\":\"" + text.toUpperCase() + "!!\",\"photoHash\":\"a5a5a5a5a5a5a5a7\"}");
        assertThat(r.get("reasons").findValuesAsText("code"), hasItems("DUPLICATE_PROFILE_TEXT", "DUPLICATE_PHOTO"));
        assertEquals(60, r.get("score").asInt());
        assertEquals("SHADOW_LIMIT", r.get("action").asText());
    }

    @Test
    void analystFalsePositiveLiftsRestrictionAndResolvingTwiceConflicts() throws Exception {
        UUID u = UUID.randomUUID(), other1 = UUID.randomUUID(), other2 = UUID.randomUUID(), analyst = UUID.randomUUID();
        String net = "\"ip\":\"198.51.100.9\",\"deviceHash\":\"dev-office\"";
        signal(other1, "{\"type\":\"LOGIN\"," + net + "}");
        signal(other2, "{\"type\":\"LOGIN\"," + net + "}");
        var r = signal(u, "{\"type\":\"LOGIN\"," + net + ",\"emailDomain\":\"yopmail.com\"}");
        assertEquals("SHADOW_LIMIT", r.get("action").asText());

        var cases = json.readTree(mvc.perform(get("/api/v1/fraud/admin/cases").with(admin(analyst))).andReturn().getResponse().getContentAsString());
        String caseId = null;
        for (JsonNode c : cases) if (c.get("userId").asText().equals(u.toString())) caseId = c.get("id").asText();
        assertNotNull(caseId);

        mvc.perform(post("/api/v1/fraud/admin/cases/" + caseId + "/resolve").with(admin(analyst))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"FALSE_POSITIVE\",\"notes\":\"shared office wifi\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED")).andExpect(jsonPath("$.label").value("FALSE_POSITIVE"));
        mvc.perform(post("/api/v1/fraud/admin/cases/" + caseId + "/resolve").with(admin(analyst))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"TRUE_POSITIVE\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/fraud/users/" + u + "/risk").with(admin(analyst)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveAction").value("ALLOW"))
                .andExpect(jsonPath("$.manualAction").value("ALLOW"))
                .andExpect(jsonPath("$.history[0].reasons[0].code").exists());

        // an analyst override sticks: later automatic escalation does not re-open a case or restrict the user
        signal(u, "{\"type\":\"LOGIN\"," + net + ",\"emailDomain\":\"yopmail.com\"}");
        mvc.perform(get("/api/v1/fraud/users/" + u + "/risk").with(admin(analyst))).andExpect(jsonPath("$.effectiveAction").value("ALLOW"));
    }

    @Test
    void ruleEditsAreAppliedToLaterEvaluations() throws Exception {
        UUID admin = UUID.randomUUID(), u = UUID.randomUUID();
        mvc.perform(put("/api/v1/fraud/admin/rules/PAYMENT_FAILURES").with(admin(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"weight\":25}")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(put("/api/v1/fraud/admin/rules/PAYMENT_FAILURES").with(user(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"weight\":25}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/fraud/admin/rules/NOPE").with(admin(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"weight\":5}")).andExpect(status().isNotFound());

        for (int i = 0; i < 4; i++) listener.handle("pj.payment.failed.v1", "{\"eventType\":\"PaymentFailed\",\"payload\":{\"userId\":\"" + u + "\"}}");
        mvc.perform(get("/api/v1/fraud/users/" + u + "/risk").with(admin(admin))).andExpect(jsonPath("$.score").value(0));

        mvc.perform(put("/api/v1/fraud/admin/rules/PAYMENT_FAILURES").with(admin(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"weight\":25}")).andExpect(status().isOk());
        listener.handle("pj.payment.failed.v1", "{\"eventType\":\"PaymentFailed\",\"payload\":{\"userId\":\"" + u + "\"}}");
        mvc.perform(get("/api/v1/fraud/users/" + u + "/risk").with(admin(admin))).andExpect(jsonPath("$.score").value(25));
    }

    @Test
    void kafkaListenerParsesChatPaymentAndMediaEventsDefensively() throws Exception {
        UUID scammer = UUID.randomUUID(), admin = UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            listener.handle("pj.chat.message-sent.v1", "{\"payload\":{\"senderId\":\"" + scammer + "\",\"recipientId\":\"" + UUID.randomUUID()
                    + "\",\"text\":\"Emergency! my mother is in hospital, please send money by western union or buy gift cards\"}}");
        }
        listener.handle("pj.chat.message-sent.v1", "not json at all");                      // skipped, no exception
        listener.handle("pj.chat.message-sent.v1", "{\"payload\":{\"text\":\"no sender\"}}"); // skipped
        listener.handle("pj.media.photo-uploaded.v1", "{\"payload\":{\"userId\":\"" + scammer + "\",\"phash\":\"0f0f0f0f0f0f0f0f\"}}");
        mvc.perform(get("/api/v1/fraud/users/" + scammer + "/risk").with(admin(admin)))
                .andExpect(status().isOk())
                // scam phrases saturate at 40 points; 6 distinct recipients in an hour add 6/15*30 = 12
                .andExpect(jsonPath("$.score").value(52))
                .andExpect(jsonPath("$.effectiveAction").value("STEP_UP_VERIFICATION"))
                .andExpect(jsonPath("$.history[0].reasons[*].code", hasItems("SCAM_KEYWORDS", "MESSAGE_BLAST")));
    }
}
