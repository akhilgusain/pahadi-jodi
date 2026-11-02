package in.pahadijodi.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false"})
class AuthFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode call(String path, String body, int status) throws Exception {
        var res = mvc.perform(MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    @Test
    void registerLoginRefreshRotationAndReuseDetection() throws Exception {
        var reg = call("/api/v1/auth/register", "{\"email\":\"a@b.in\",\"password\":\"correct-horse-9\"}", 201);
        call("/api/v1/auth/register", "{\"email\":\"A@b.in\",\"password\":\"correct-horse-9\"}", 409);
        call("/api/v1/auth/login", "{\"email\":\"a@b.in\",\"password\":\"wrong-password-1\"}", 401);
        call("/api/v1/auth/login", "{\"email\":\"a@b.in\",\"password\":\"correct-horse-9\"}", 200);

        String r1 = reg.get("refreshToken").asText();
        var rotated = call("/api/v1/auth/refresh", "{\"refreshToken\":\"" + r1 + "\"}", 200);
        // replaying the old token = theft signal: 401 and the whole family is revoked
        call("/api/v1/auth/refresh", "{\"refreshToken\":\"" + r1 + "\"}", 401);
        call("/api/v1/auth/refresh", "{\"refreshToken\":\"" + rotated.get("refreshToken").asText() + "\"}", 401);
    }

    @Test
    void validationAndJwks() throws Exception {
        call("/api/v1/auth/register", "{\"email\":\"nope\",\"password\":\"short\"}", 422);
        mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }
}
