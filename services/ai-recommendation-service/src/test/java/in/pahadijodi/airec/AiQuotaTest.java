package in.pahadijodi.airec;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import in.pahadijodi.airec.service.ProfileEventsConsumer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:airecquota;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false", "pj.kafka.consumers.enabled=false", "pj.ai.daily-quota=2"})
class AiQuotaTest {
    @Autowired MockMvc mvc;
    @Autowired ProfileEventsConsumer consumer;

    @Test
    void dailyQuotaIsEnforcedPerUserAndSafetyChecksAreFree() throws Exception {
        UUID user = UUID.randomUUID();
        consumer.onProfileUpdated("{\"payload\":{\"profileId\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + user + "\",\"gender\":\"MALE\",\"age\":30,"
                + "\"community\":\"KUMAONI\",\"district\":\"ALMORA\",\"division\":\"KUMAON\",\"completeness\":60}}");
        var auth = jwt().jwt(j -> j.subject(user.toString()));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/ai/profile/bio-suggestions").with(auth).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"draft\":\"q" + i + "\"}")).andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/ai/profile/bio-suggestions").with(auth).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AI_QUOTA_EXCEEDED"));
        mvc.perform(post("/api/v1/ai/safety/check").with(auth).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hello\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/ai/usage").with(auth)).andExpect(jsonPath("$.used").value(2)).andExpect(jsonPath("$.limit").value(2));
        // another user is unaffected
        mvc.perform(get("/api/v1/ai/usage").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))).andExpect(jsonPath("$.used").value(0));
    }
}
