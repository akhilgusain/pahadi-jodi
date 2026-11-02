package in.pahadijodi.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import in.pahadijodi.admin.client.FakeUserDirectoryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Own database: this test deliberately corrupts the audit table and must not affect other tests. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admintamper;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false"})
class AuditTamperTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void databaseLevelTamperingIsDetectedByVerify() throws Exception {
        var admin = jwt().jwt(j -> j.subject("admin-a")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        for (int i = 10; i < 14; i++) {
            mvc.perform(post("/api/v1/admin/users/" + FakeUserDirectoryClient.idFor(i) + "/force-logout").with(admin)).andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/v1/admin/audit/verify").with(admin)).andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.checked").value(4));

        // an attacker with database access rewrites who did what (no application code path can do this)
        jdbc.update("UPDATE admin_audit_log SET actor_id = 'someone-else' WHERE seq = 2");
        mvc.perform(get("/api/v1/admin/audit/verify").with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false)).andExpect(jsonPath("$.brokenAtSeq").value(2))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("modified")));

        jdbc.update("UPDATE admin_audit_log SET actor_id = 'admin-a' WHERE seq = 2");   // restored
        mvc.perform(get("/api/v1/admin/audit/verify").with(admin)).andExpect(jsonPath("$.valid").value(true));

        jdbc.update("DELETE FROM admin_audit_log WHERE seq = 4");   // truncating the tail
        mvc.perform(get("/api/v1/admin/audit/verify").with(admin)).andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("audit_head")));
    }
}
