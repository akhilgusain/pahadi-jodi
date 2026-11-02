package in.pahadijodi.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.outbox.OutboxRepository;
import in.pahadijodi.moderation.domain.Enums.AccountStatus;
import in.pahadijodi.moderation.domain.UserStatus;
import in.pahadijodi.moderation.domain.UserStatusRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:moderation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false", "pj.kafka.consumers.enabled=false"})
class ModerationApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxRepository outbox;
    @Autowired UserStatusRepository statuses;

    static final String SCAM = "please send money through western union to my friend";

    static RequestPostProcessor as(UUID user, String... roles) {
        var p = jwt().jwt(j -> j.subject(user.toString()));
        var auths = new SimpleGrantedAuthority[roles.length];
        for (int i = 0; i < roles.length; i++) auths[i] = new SimpleGrantedAuthority("ROLE_" + roles[i]);
        return roles.length == 0 ? p : p.authorities(auths);
    }

    JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    JsonNode check(UUID user, String type, String text, String refId) throws Exception {
        String payload = json.writeValueAsString(java.util.Map.of("contentType", type,
                "text", text == null ? "" : text, "refId", refId == null ? "" : refId));
        return body(mvc.perform(post("/api/v1/moderation/check").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()));
    }

    String scamCase(UUID subject) throws Exception {
        JsonNode v = check(subject, "TEXT", SCAM, null);
        assertThat(v.get("decision").asText()).isEqualTo("FLAG");
        return v.get("caseId").asText();
    }

    JsonNode decide(String caseId, String decision, UUID moderator, String... roles) throws Exception {
        return body(mvc.perform(post("/api/v1/moderation/cases/" + caseId + "/decision").with(as(moderator, roles))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"" + decision + "\",\"note\":\"n\"}")).andExpect(status().isOk()));
    }

    JsonNode userStatus(UUID u) throws Exception {
        return body(mvc.perform(get("/api/v1/moderation/users/" + u + "/status").with(as(UUID.randomUUID(), "MODERATOR"))).andExpect(status().isOk()));
    }

    boolean outboxHas(String topic, String... fragments) {
        return outbox.findAll().stream().anyMatch(e -> e.getTopic().equals(topic)
                && java.util.Arrays.stream(fragments).allMatch(e.getEnvelope()::contains));
    }

    // ---- automated check -----------------------------------------------------------------------------

    @Test
    void checkReturnsAllowFlagBlockAndOnlyOpensCasesWhenNeeded() throws Exception {
        UUID u = UUID.randomUUID();
        JsonNode ok = check(u, "TEXT", "Namaste, I loved your profile. Which part of Almora are you from?", null);
        assertThat(ok.get("decision").asText()).isEqualTo("ALLOW");
        assertThat(ok.get("caseId").isNull()).isTrue();

        JsonNode flag = check(u, "TEXT", "my number is 98765 43210 call me", "msg-1");
        assertThat(flag.get("decision").asText()).isEqualTo("FLAG");
        assertThat(flag.get("labels").get(0).get("name").asText()).isEqualTo("CONTACT_SHARING");
        assertThat(flag.get("caseId").isNull()).isFalse();

        JsonNode block = check(u, "TEXT", "you stupid bastard", "msg-2");
        assertThat(block.get("decision").asText()).isEqualTo("BLOCK");
        assertThat(outboxHas("pj.moderation.decision.v1", block.get("caseId").asText(), "\"outcome\":\"BLOCK\"", "AUTO")).isTrue();
    }

    @Test
    void checkValidatesInputAndIgnoresSubjectOverrideFromNormalUsers() throws Exception {
        UUID u = UUID.randomUUID(), victim = UUID.randomUUID();
        mvc.perform(post("/api/v1/moderation/check").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"TEXT\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/moderation/check").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"IMAGE\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/moderation/check")).andExpect(status().isUnauthorized());

        // a regular user cannot frame someone else; a service token can act for a user
        String payload = "{\"contentType\":\"TEXT\",\"text\":\"" + SCAM + "\",\"subjectUserId\":\"" + victim + "\"}";
        String c1 = body(mvc.perform(post("/api/v1/moderation/check").with(as(u)).contentType(MediaType.APPLICATION_JSON).content(payload))).get("caseId").asText();
        String c2 = body(mvc.perform(post("/api/v1/moderation/check").with(as(UUID.randomUUID(), "SERVICE")).contentType(MediaType.APPLICATION_JSON).content(payload))).get("caseId").asText();
        UUID mod = UUID.randomUUID();
        assertThat(body(mvc.perform(get("/api/v1/moderation/cases/" + c1).with(as(mod, "MODERATOR")))).get("subjectUserId").asText()).isEqualTo(u.toString());
        assertThat(body(mvc.perform(get("/api/v1/moderation/cases/" + c2).with(as(mod, "MODERATOR")))).get("subjectUserId").asText()).isEqualTo(victim.toString());
    }

    @Test
    void reviewersSeeRedactedExcerpts() throws Exception {
        UUID u = UUID.randomUUID();
        String id = check(u, "TEXT", "write me at secret.person@example.com or 9876543210 please", null).get("caseId").asText();
        String excerpt = body(mvc.perform(get("/api/v1/moderation/cases/" + id).with(as(UUID.randomUUID(), "MODERATOR")))).get("excerpt").asText();
        assertThat(excerpt).doesNotContain("secret.person").doesNotContain("9876543210").contains("***");
    }

    // ---- queue & human decisions ----------------------------------------------------------------------------

    @Test
    void queueIsOrderedByPriorityAndRestrictedToModerators() throws Exception {
        UUID u = UUID.randomUUID(), mod = UUID.randomUUID();
        String low = check(u, "TEXT", "ping me on 9876543210", null).get("caseId").asText();        // FLAG, priority 3
        String mid = scamCase(u);                                                                      // FLAG, severe -> 2
        String top = check(u, "TEXT", "you bastard", null).get("caseId").asText();                    // BLOCK -> 1

        mvc.perform(get("/api/v1/moderation/cases").with(as(u))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/moderation/cases")).andExpect(status().isUnauthorized());

        JsonNode list = body(mvc.perform(get("/api/v1/moderation/cases?size=100").with(as(mod, "MODERATOR"))).andExpect(status().isOk()));
        var order = new java.util.ArrayList<String>();
        for (JsonNode c : list) {
            String id = c.get("id").asText();
            if (id.equals(low) || id.equals(mid) || id.equals(top)) order.add(id);
        }
        assertThat(order).containsExactly(top, mid, low);

        JsonNode topCase = body(mvc.perform(get("/api/v1/moderation/cases/" + top).with(as(mod, "MODERATOR"))));
        assertThat(topCase.get("priority").asInt()).isEqualTo(1);
        assertThat(topCase.get("overdue").asBoolean()).isFalse();
        assertThat(Duration.between(Instant.parse(topCase.get("createdAt").asText()), Instant.parse(topCase.get("dueAt").asText())))
                .isEqualTo(Duration.ofHours(4));                                                       // SLA by priority
    }

    @Test
    void assignDecideEscalateAndAuditTrail() throws Exception {
        UUID u = UUID.randomUUID(), mod = UUID.randomUUID(), admin = UUID.randomUUID();
        String id = scamCase(u);

        JsonNode assigned = body(mvc.perform(post("/api/v1/moderation/cases/" + id + "/assign").with(as(mod, "MODERATOR"))).andExpect(status().isOk()));
        assertThat(assigned.get("status").asText()).isEqualTo("IN_REVIEW");
        assertThat(assigned.get("assignedTo").asText()).isEqualTo(mod.toString());

        JsonNode esc = decide(id, "ESCALATE", mod, "MODERATOR");
        assertThat(esc.get("status").asText()).isEqualTo("ESCALATED");
        assertThat(esc.get("priority").asInt()).isEqualTo(1);

        // an escalated case is out of the moderator's hands
        mvc.perform(post("/api/v1/moderation/cases/" + id + "/decision").with(as(mod, "MODERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}")).andExpect(status().isForbidden());
        JsonNode done = decide(id, "APPROVE", admin, "ADMIN");
        assertThat(done.get("status").asText()).isEqualTo("RESOLVED");
        assertThat(done.get("resolution").asText()).isEqualTo("APPROVE");
        assertThat(userStatus(u).get("activePoints").asLong()).isZero();                               // approve = no strike

        mvc.perform(post("/api/v1/moderation/cases/" + id + "/decision").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}")).andExpect(status().isConflict());

        JsonNode trail = body(mvc.perform(get("/api/v1/moderation/cases/" + id + "/audit").with(as(mod, "MODERATOR"))));
        var actions = new java.util.ArrayList<String>();
        trail.forEach(a -> actions.add(a.get("action").asText()));
        assertThat(actions).containsExactly("AUTO_FLAG", "ASSIGNED", "ESCALATED", "APPROVED");
        assertThat(outboxHas("pj.moderation.decision.v1", id, "\"outcome\":\"APPROVED\"", "HUMAN")).isTrue();
    }

    // ---- strikes, bans, appeals ----------------------------------------------------------------------------------

    @Test
    void strikeLadderEndsInPermanentBanAndAppealStepsItBack() throws Exception {
        UUID u = UUID.randomUUID(), mod = UUID.randomUUID(), admin = UUID.randomUUID();
        assertThat(userStatus(u).get("status").asText()).isEqualTo("ACTIVE");

        String c1 = scamCase(u);
        decide(c1, "REJECT", mod, "MODERATOR");                                                        // 2 points
        assertThat(userStatus(u).get("status").asText()).isEqualTo("ACTIVE");

        decide(scamCase(u), "REJECT", mod, "MODERATOR");                                               // 4 points -> 7-day suspension
        JsonNode s = userStatus(u);
        assertThat(s.get("status").asText()).isEqualTo("SUSPENDED");
        assertThat(s.get("activePoints").asLong()).isEqualTo(4);
        assertThat(Duration.between(Instant.now(), Instant.parse(s.get("until").asText())).toDays()).isBetween(5L, 7L);

        decide(scamCase(u), "REJECT", mod, "MODERATOR");                                               // 6 points -> 30 days
        assertThat(Duration.between(Instant.now(), Instant.parse(userStatus(u).get("until").asText())).toDays()).isBetween(28L, 30L);

        String last = scamCase(u);
        decide(last, "REJECT", mod, "MODERATOR");                                                      // 8 points -> permanent
        assertThat(userStatus(u).get("status").asText()).isEqualTo("BANNED");
        assertThat(outboxHas("pj.user.banned.v1", u.toString(), "PERMANENT")).isTrue();

        // appeal: only the owner can appeal, one open appeal per case, only admins decide
        mvc.perform(post("/api/v1/moderation/appeals").with(as(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"" + last + "\",\"message\":\"not mine\"}")).andExpect(status().isNotFound());
        String appealId = body(mvc.perform(post("/api/v1/moderation/appeals").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"" + last + "\",\"message\":\"That was my sister's message, please review\"}"))
                .andExpect(status().isCreated())).get("id").asText();
        mvc.perform(post("/api/v1/moderation/appeals").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"" + last + "\",\"message\":\"again\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/moderation/appeals/me").with(as(u))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/v1/moderation/appeals/" + appealId + "/decision").with(as(mod, "MODERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"overturn\":true}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/moderation/appeals").with(as(admin, "ADMIN"))).andExpect(status().isOk());

        mvc.perform(post("/api/v1/moderation/appeals/" + appealId + "/decision").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"overturn\":true,\"note\":\"misattributed\"}")).andExpect(jsonPath("$.status").value("OVERTURNED"));
        JsonNode after = userStatus(u);
        assertThat(after.get("activePoints").asLong()).isEqualTo(6);                                   // that case's 2 points no longer count
        assertThat(after.get("status").asText()).isEqualTo("SUSPENDED");                               // permanent -> 30-day suspension
        assertThat(outboxHas("pj.user.banned.v1", u.toString(), "TEMPORARY")).isTrue();
    }

    @Test
    void appealsNeedSomethingToAppeal() throws Exception {
        UUID u = UUID.randomUUID();
        mvc.perform(post("/api/v1/moderation/appeals").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"please unlock me\"}")).andExpect(status().isUnprocessableEntity());
        String c = scamCase(u);                                                                        // FLAG only: no adverse decision yet
        mvc.perform(post("/api/v1/moderation/appeals").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"" + c + "\",\"message\":\"x\"}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void manualBansRespectRolesAndTemporarySuspensionsExpire() throws Exception {
        UUID target = UUID.randomUUID(), mod = UUID.randomUUID(), admin = UUID.randomUUID();
        mvc.perform(post("/api/v1/moderation/users/" + target + "/ban").with(as(mod, "MODERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"permanent\":true,\"reason\":\"fraud\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/moderation/users/" + target + "/ban").with(as(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"permanent\":false,\"days\":3,\"reason\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/moderation/users/" + target + "/ban").with(as(mod, "MODERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"permanent\":false,\"days\":365,\"reason\":\"x\"}")).andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/v1/moderation/users/" + target + "/ban").with(as(mod, "MODERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"permanent\":false,\"days\":3,\"reason\":\"abusive chat\"}")).andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(post("/api/v1/moderation/users/" + target + "/ban").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"permanent\":true,\"reason\":\"romance scam\"}")).andExpect(jsonPath("$.status").value("BANNED"));

        // strikes never override an admin's manual ban
        decide(scamCase(target), "REJECT", mod, "MODERATOR");
        assertThat(userStatus(target).get("status").asText()).isEqualTo("BANNED");

        mvc.perform(post("/api/v1/moderation/users/" + target + "/unban").with(as(mod, "MODERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"mistake\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/moderation/users/" + target + "/unban").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"mistake\"}")).andExpect(jsonPath("$.status").value("ACTIVE"));

        // a suspension that has run out reads as ACTIVE
        UUID expired = UUID.randomUUID();
        UserStatus s = new UserStatus(expired);
        s.set(AccountStatus.SUSPENDED, Instant.now().minus(Duration.ofDays(1)), "MANUAL", "old");
        statuses.save(s);
        assertThat(userStatus(expired).get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void statusIsVisibleToSelfAndStaffOnly() throws Exception {
        UUID u = UUID.randomUUID();
        mvc.perform(get("/api/v1/moderation/users/" + u + "/status").with(as(u))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/moderation/users/" + u + "/status").with(as(UUID.randomUUID()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/moderation/users/" + u + "/status").with(as(UUID.randomUUID(), "SERVICE"))).andExpect(status().isOk());
    }

    // ---- reports ---------------------------------------------------------------------------------------------------

    @Test
    void reportsCreateCasesDedupeAndEscalateWhenSeveralPeopleReport() throws Exception {
        UUID target = UUID.randomUUID(), r1 = UUID.randomUUID(), r2 = UUID.randomUUID(), r3 = UUID.randomUUID(), mod = UUID.randomUUID();
        mvc.perform(post("/api/v1/moderation/reports").with(as(target)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":\"" + target + "\",\"reason\":\"SPAM\"}")).andExpect(status().isUnprocessableEntity());

        String body1 = "{\"targetUserId\":\"" + target + "\",\"reason\":\"FAKE_PROFILE\",\"details\":\"photos look stolen\"}";
        JsonNode first = body(mvc.perform(post("/api/v1/moderation/reports").with(as(r1)).contentType(MediaType.APPLICATION_JSON).content(body1))
                .andExpect(status().isCreated()));
        mvc.perform(post("/api/v1/moderation/reports").with(as(r1)).contentType(MediaType.APPLICATION_JSON).content(body1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
        assertThat(body(mvc.perform(get("/api/v1/moderation/cases/" + first.get("caseId").asText()).with(as(mod, "MODERATOR")))).get("priority").asInt()).isEqualTo(3);

        mvc.perform(post("/api/v1/moderation/reports").with(as(r2)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":\"" + target + "\",\"reason\":\"SCAM\"}")).andExpect(status().isCreated());
        JsonNode third = body(mvc.perform(post("/api/v1/moderation/reports").with(as(r3)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":\"" + target + "\",\"reason\":\"HARASSMENT\"}")).andExpect(status().isCreated()));

        // three distinct reporters in a week: every open report case about this user jumps to priority 1
        for (String caseId : new String[]{first.get("caseId").asText(), third.get("caseId").asText()}) {
            JsonNode c = body(mvc.perform(get("/api/v1/moderation/cases/" + caseId).with(as(mod, "MODERATOR"))));
            assertThat(c.get("priority").asInt()).isEqualTo(1);
            assertThat(c.get("contentType").asText()).isEqualTo("REPORT");
            assertThat(c.get("subjectUserId").asText()).isEqualTo(target.toString());
        }
    }

    // ---- audit -----------------------------------------------------------------------------------------------------------

    @Test
    void auditChainVerifiesAndIsAdminOnly() throws Exception {
        scamCase(UUID.randomUUID());
        mvc.perform(get("/api/v1/moderation/audit/verify").with(as(UUID.randomUUID(), "MODERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/moderation/audit/verify").with(as(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intact").value(true));
    }
}
