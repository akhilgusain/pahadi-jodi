package in.pahadijodi.profile;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
        "spring.datasource.url=jdbc:h2:mem:profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false"})
class ProfileApiTest {
    @Autowired MockMvc mvc;

    static final String USER = "0b8f4c0e-6a54-4b52-9f0a-1d1d6d6f7a01";
    static final String BODY = """
        {"displayName":"Anjali Rawat","gender":"FEMALE","dateOfBirth":"1996-04-12","community":"GARHWALI",
         "caste":"Rajput","gotra":"Kashyap","district":"PAURI_GARHWAL","village":"Kandai","education":"B.Tech",
         "occupation":"Software Engineer"}""";

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/v1/profiles/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void createReadAndValidate() throws Exception {
        var auth = jwt().jwt(j -> j.subject(USER));
        mvc.perform(get("/api/v1/profiles/me").with(auth)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/profiles/me").with(auth).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.division").value("GARHWAL"))
                .andExpect(jsonPath("$.completeness").value(75));
        mvc.perform(get("/api/v1/profiles/me").with(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Anjali Rawat"));
        mvc.perform(put("/api/v1/profiles/me").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"\",\"district\":\"MARS\"}")).andExpect(status().is4xxClientError());
        mvc.perform(put("/api/v1/profiles/me").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("1996-04-12", "2015-01-01"))).andExpect(status().isUnprocessableEntity());
    }
}
