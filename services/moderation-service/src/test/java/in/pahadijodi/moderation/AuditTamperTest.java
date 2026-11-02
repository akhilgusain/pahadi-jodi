package in.pahadijodi.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import in.pahadijodi.moderation.service.AuditService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Own in-memory database: this test deliberately corrupts the audit log. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:moderation_audit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false", "pj.kafka.consumers.enabled=false"})
class AuditTamperTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditService audit;

    @Test
    void editingAnyAuditRowBreaksTheChain() throws Exception {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/moderation/check").with(jwt().jwt(j -> j.subject(user.toString())))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"contentType\":\"TEXT\",\"text\":\"you stupid bastard\"}"))
                    .andExpect(status().isOk());
        }
        var before = audit.verify();
        assertThat(before.intact()).isTrue();
        assertThat(before.entries()).isEqualTo(3);

        Long target = jdbc.queryForObject("select seq from decision_audit order by seq limit 1 offset 1", Long.class);
        jdbc.update("update decision_audit set detail = 'nothing to see here' where seq = ?", target);

        var after = audit.verify();
        assertThat(after.intact()).isFalse();
        assertThat(after.firstBrokenSeq()).isEqualTo(target);
        mvc.perform(get("/api/v1/moderation/audit/verify").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intact").value(false));
    }
}
