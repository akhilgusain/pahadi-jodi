package in.pahadijodi.admin;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.admin.client.FakeUserDirectoryClient;
import in.pahadijodi.common.outbox.OutboxRepository;
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
        "spring.datasource.url=jdbc:h2:mem:admintest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false"})
class AdminApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxRepository outbox;
    @Autowired FakeUserDirectoryClient fakeUsers;

    static RequestPostProcessor as(String sub, String... roles) {
        var auth = new SimpleGrantedAuthority[roles.length];
        for (int i = 0; i < roles.length; i++) auth[i] = new SimpleGrantedAuthority("ROLE_" + roles[i]);
        return jwt().jwt(j -> j.subject(sub)).authorities(auth);
    }
    static final RequestPostProcessor ADMIN_A = as("admin-a", "ADMIN", "USER");
    static final RequestPostProcessor ADMIN_B = as("admin-b", "SUPER_ADMIN", "USER");
    static final RequestPostProcessor MOD = as("mod-1", "MODERATOR", "USER");

    private String send(String path, RequestPostProcessor who, String body) throws Exception {
        return mvc.perform(post(path).with(who).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString();
    }

    // ---- security & dashboard -----------------------------------------------------------------------------------

    @Test
    void endpointsRequireAuthenticationAndTheRightRole() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/dashboard").with(as("u", "USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/audit").with(MOD)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/approvals").with(as("sup", "SUPPORT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").with(as("sup", "SUPPORT"))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/users/" + FakeUserDirectoryClient.idFor(1) + "/deactivate").with(MOD)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
    }

    @Test
    void dashboardAggregatesKpisAndHidesRevenueFromNonFinanceRoles() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard").with(ADMIN_A)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(12840))
                .andExpect(jsonPath("$.subscriptionsByPlan.VIP").value(161))
                .andExpect(jsonPath("$.openFraudCases").value(7))
                .andExpect(jsonPath("$.moderationQueue").value(23))
                .andExpect(jsonPath("$.revenueMonthToDatePaise").isNumber())
                .andExpect(jsonPath("$.degradedSources", hasSize(0)));
        mvc.perform(get("/api/v1/admin/dashboard").with(as("fin", "FINANCE"))).andExpect(jsonPath("$.revenueMonthToDatePaise").isNumber());
        mvc.perform(get("/api/v1/admin/dashboard").with(MOD)).andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueMonthToDatePaise").doesNotExist())
                .andExpect(jsonPath("$.openFraudCases").value(7));
    }

    // ---- user management ---------------------------------------------------------------------------------------

    @Test
    void searchAndViewUsersWithMaskedEmails() throws Exception {
        mvc.perform(get("/api/v1/admin/users").param("q", "rawat").with(ADMIN_A)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.items[0].maskedEmail", containsString("***@")));
        mvc.perform(get("/api/v1/admin/users").param("size", "5").with(ADMIN_A)).andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.total").value(25));
        mvc.perform(get("/api/v1/admin/users/" + FakeUserDirectoryClient.idFor(0)).with(ADMIN_A)).andExpect(jsonPath("$.displayName").value("Anjali Rawat"));
        mvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID()).with(ADMIN_A)).andExpect(status().isNotFound());
    }

    @Test
    void shortBanIsAppliedImmediatelyByAModerator() throws Exception {
        UUID u = FakeUserDirectoryClient.idFor(2);
        mvc.perform(post("/api/v1/admin/users/" + u + "/bans").with(MOD).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"abusive messages\",\"durationDays\":14}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.outcome").value("APPLIED"));
        mvc.perform(get("/api/v1/admin/users/" + u).with(MOD)).andExpect(jsonPath("$.status").value("BANNED"));
        mvc.perform(post("/api/v1/admin/users/" + u + "/bans").with(MOD).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"again\",\"durationDays\":3}")).andExpect(status().isConflict());
        assertTrue(outbox.findAll().stream().anyMatch(e -> e.getTopic().equals("pj.admin.user-banned.v1") && e.getPartitionKey().equals(u.toString())));

        // lifting a short ban needs no second approver
        mvc.perform(post("/api/v1/admin/users/" + u + "/unban").with(MOD).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"apologised\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.outcome").value("APPLIED"));
        mvc.perform(get("/api/v1/admin/users/" + u).with(MOD)).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/v1/admin/users/" + u + "/unban").with(MOD).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void banDurationValidation() throws Exception {
        UUID u = FakeUserDirectoryClient.idFor(3);
        mvc.perform(post("/api/v1/admin/users/" + u + "/bans").with(ADMIN_A).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_BAN_DURATION"));
        mvc.perform(post("/api/v1/admin/users/" + u + "/bans").with(ADMIN_A).contentType(MediaType.APPLICATION_JSON).content("{\"durationDays\":3}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void permanentBanNeedsASecondSuperAdmin() throws Exception {
        UUID u = FakeUserDirectoryClient.idFor(4);
        String res = send("/api/v1/admin/users/" + u + "/bans", ADMIN_A, "{\"reason\":\"romance scam confirmed\",\"permanent\":true}");
        var node = json.readTree(res);
        assertEquals("PENDING_APPROVAL", node.get("outcome").asText());
        String requestId = node.get("requestId").asText();
        mvc.perform(get("/api/v1/admin/users/" + u).with(ADMIN_A)).andExpect(jsonPath("$.status").value("ACTIVE"));   // not applied yet

        mvc.perform(post("/api/v1/admin/users/" + u + "/bans").with(ADMIN_A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"dup\",\"permanent\":true}")).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/admin/approvals").with(ADMIN_B)).andExpect(jsonPath("$[?(@.id=='" + requestId + "')].kind", contains("BAN")));

        mvc.perform(post("/api/v1/admin/approvals/" + requestId + "/approve").with(ADMIN_A)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MAKER_CHECKER_VIOLATION"));
        mvc.perform(post("/api/v1/admin/approvals/" + requestId + "/approve").with(MOD)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/approvals/" + requestId + "/approve").with(ADMIN_B)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.decidedBy").value("admin-b"));
        mvc.perform(post("/api/v1/admin/approvals/" + requestId + "/approve").with(ADMIN_B)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/admin/users/" + u).with(ADMIN_A)).andExpect(jsonPath("$.status").value("BANNED"));

        // lifting a permanent ban is also maker-checker
        var un = json.readTree(send("/api/v1/admin/users/" + u + "/unban", ADMIN_B, "{\"reason\":\"appeal upheld\"}"));
        assertEquals("PENDING_APPROVAL", un.get("outcome").asText());
        mvc.perform(post("/api/v1/admin/approvals/" + un.get("requestId").asText() + "/approve").with(ADMIN_A)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users/" + u).with(ADMIN_A)).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void rejectedRequestDoesNotApplyTheBan() throws Exception {
        UUID u = FakeUserDirectoryClient.idFor(5);
        String id = json.readTree(send("/api/v1/admin/users/" + u + "/bans", ADMIN_A, "{\"reason\":\"suspected\",\"durationDays\":90}")).get("requestId").asText();
        mvc.perform(post("/api/v1/admin/approvals/" + id + "/reject").with(ADMIN_B).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"insufficient evidence\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/v1/admin/users/" + u).with(ADMIN_A)).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deactivateAndForceLogoutPublishEventsAndCallDownstream() throws Exception {
        UUID u = FakeUserDirectoryClient.idFor(6);
        mvc.perform(post("/api/v1/admin/users/" + u + "/deactivate").with(ADMIN_A).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"user request\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/admin/users/" + u + "/force-logout").with(as("sup", "SUPPORT"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/users/" + u).with(ADMIN_A)).andExpect(jsonPath("$.status").value("DEACTIVATED"));
        assertTrue(fakeUsers.actions.contains("FORCE_LOGOUT:" + u));
        var topics = outbox.findAll().stream().map(e -> e.getTopic()).toList();
        assertThat(topics, hasItems("pj.admin.user-deactivated.v1", "pj.admin.force-logout.v1"));
    }

    // ---- content management ------------------------------------------------------------------------------------

    @Test
    void contentLifecycleWithPublicReadsOnlyForPublishedItems() throws Exception {
        var editor = as("editor-1", "CONTENT_EDITOR", "USER");
        String body = "{\"type\":\"PAGE\",\"slug\":\"about-us\",\"locale\":\"en\",\"title\":\"About Pahadi Jodi\",\"body\":\"Made for the hills\",\"sortOrder\":0}";
        String id = json.readTree(mvc.perform(post("/api/v1/admin/content").with(editor).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn().getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/v1/admin/content").with(editor).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/content").with(editor).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("about-us", "Bad Slug!"))).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/content/pages/about-us")).andExpect(status().isNotFound());                 // drafts are never public
        mvc.perform(get("/api/v1/admin/content").with(MOD)).andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/content/" + id + "/publish").with(editor)).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/content/pages/about-us")).andExpect(status().isOk())            // no Authorization header
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(jsonPath("$.title").value("About Pahadi Jodi"));
        mvc.perform(get("/api/v1/content/pages/about-us").param("locale", "hi")).andExpect(status().isOk());   // falls back to English

        mvc.perform(put("/api/v1/admin/content/" + id).with(editor).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("About Pahadi Jodi", "About Us"))).andExpect(jsonPath("$.title").value("About Us"));
        mvc.perform(delete("/api/v1/admin/content/" + id).with(editor)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/content/" + id + "/archive").with(editor)).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/v1/content/pages/about-us")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/admin/content/" + id).with(editor)).andExpect(status().isNoContent());
        // public CMS is read-only
        mvc.perform(post("/api/v1/content/pages/x")).andExpect(status().isUnauthorized());
    }

    @Test
    void bannersAreOrderedAndLocalised() throws Exception {
        var editor = as("editor-2", "CONTENT_EDITOR");
        for (String[] b : new String[][]{{"b-second", "Second", "2"}, {"b-first", "First", "1"}}) {
            String id = json.readTree(send("/api/v1/admin/content", editor, "{\"type\":\"BANNER\",\"slug\":\"" + b[0] + "\",\"locale\":\"en\",\"title\":\"" + b[1]
                    + "\",\"imageUrl\":\"https://cdn.example/b.jpg\",\"sortOrder\":" + b[2] + "}")).get("id").asText();
            mvc.perform(post("/api/v1/admin/content/" + id + "/publish").with(editor)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/content/banners")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("First", "Second")));
        assertTrue(outbox.findAll().stream().anyMatch(e -> e.getTopic().equals("pj.admin.content-published.v1")));
    }

    // ---- audit -------------------------------------------------------------------------------------------------

    @Test
    void everyAdminActionIsAuditedAndTheChainVerifies() throws Exception {
        UUID u = FakeUserDirectoryClient.idFor(7);
        send("/api/v1/admin/users/" + u + "/bans", MOD, "{\"reason\":\"spam\",\"durationDays\":5}");
        mvc.perform(get("/api/v1/admin/audit").with(ADMIN_A)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='USER_BANNED' && @.targetId=='" + u + "')].actorId", contains("mod-1")))
                .andExpect(jsonPath("$[0].hash", hasLength(64)));
        mvc.perform(get("/api/v1/admin/audit/verify").with(ADMIN_A)).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.checked", greaterThan(0)));
    }
}
