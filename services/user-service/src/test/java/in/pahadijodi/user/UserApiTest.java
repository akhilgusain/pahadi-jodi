package in.pahadijodi.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.outbox.OutboxEvent;
import in.pahadijodi.common.outbox.OutboxRepository;
import in.pahadijodi.user.service.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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
        "spring.datasource.url=jdbc:h2:mem:usertest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false", "pj.kafka.consumers.enabled=false"})
class UserApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxRepository outbox;
    @Autowired UserService service;
    @Autowired in.pahadijodi.user.service.IdentityEventsListener listener;

    private static RequestPostProcessor as(UUID id) { return jwt().jwt(j -> j.subject(id.toString())); }

    private long events(String topic) {
        return outbox.findAll().stream().map(OutboxEvent::getTopic).filter(topic::equals).count();
    }

    private void doPut(UUID u, String path, String body, int status) throws Exception {
        mvc.perform(put(path).with(as(u)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(status));
    }

    private void doPost(UUID u, String path, int status) throws Exception {
        mvc.perform(post(path).with(as(u))).andExpect(status().is(status));
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void lazyCreateContactMaskingAndPreferences() throws Exception {
        UUID u = UUID.randomUUID();
        mvc.perform(get("/api/v1/users/me").with(as(u))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.preferences.language").value("en"));
        doPut(u, "/api/v1/users/me/contact", "{\"email\":\"Anjali@Example.in\",\"phone\":\"+919876543210\"}", 200);
        mvc.perform(get("/api/v1/users/me").with(as(u)))
                .andExpect(jsonPath("$.contact.email").value("anjali@example.in"))
                .andExpect(jsonPath("$.contact.phoneMasked").value("+91******3210"))
                .andExpect(jsonPath("$.contact.phoneVerified").value(false))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("9876543210"))));
        doPut(u, "/api/v1/users/me/contact", "{\"phone\":\"12345\"}", 422);
        doPut(u, "/api/v1/users/me/preferences", "{\"language\":\"hi\",\"notifyEmail\":true,\"notifySms\":false,\"notifyPush\":true,"
                + "\"notifyWhatsapp\":true,\"profileVisibility\":\"VERIFIED_ONLY\",\"allowDirectMessages\":\"NONE\"}", 200);
        mvc.perform(get("/api/v1/users/me").with(as(u)))
                .andExpect(jsonPath("$.preferences.language").value("hi"))
                .andExpect(jsonPath("$.preferences.notifyWhatsapp").value(true));
        doPut(u, "/api/v1/users/me/preferences", "{\"language\":\"fr\",\"profileVisibility\":\"ALL_MEMBERS\",\"allowDirectMessages\":\"ALL\"}", 422);
    }

    @Test
    void consentsAreVersionedAndRequiredOnesCannotBeWithdrawn() throws Exception {
        UUID u = UUID.randomUUID();
        doPut(u, "/api/v1/users/me/consents", "{\"type\":\"TERMS\",\"version\":\"2026-01\",\"granted\":true}", 200);
        doPut(u, "/api/v1/users/me/consents", "{\"type\":\"MARKETING\",\"version\":\"1\",\"granted\":true}", 200);
        doPut(u, "/api/v1/users/me/consents", "{\"type\":\"MARKETING\",\"version\":\"1\",\"granted\":false}", 200);
        doPut(u, "/api/v1/users/me/consents", "{\"type\":\"TERMS\",\"version\":\"2026-01\",\"granted\":false}", 422);
        mvc.perform(get("/api/v1/users/me/consents").with(as(u)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.type=='MARKETING')].granted").value(false))
                .andExpect(jsonPath("$[?(@.type=='TERMS')].granted").value(true));
    }

    @Test
    void devicesRegisterListRevokeAndOwnership() throws Exception {
        UUID u = UUID.randomUUID(), other = UUID.randomUUID();
        String res = mvc.perform(post("/api/v1/users/me/devices").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pixel 8\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(res).get("id").asText();
        mvc.perform(get("/api/v1/users/me/devices").with(as(u))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/v1/users/me/devices/" + id).with(as(other))).andExpect(status().isNotFound());
        long before = events("pj.user.device-revoked.v1");
        mvc.perform(delete("/api/v1/users/me/devices/" + id).with(as(u))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me/devices").with(as(u))).andExpect(jsonPath("$.length()").value(0));
        assertThat(events("pj.user.device-revoked.v1")).isEqualTo(before + 1);
    }

    @Test
    void deactivateReactivateStateMachine() throws Exception {
        UUID u = UUID.randomUUID();
        doPost(u, "/api/v1/users/me/reactivate", 409);               // active -> reactivate is invalid
        doPost(u, "/api/v1/users/me/deactivate", 200);
        doPost(u, "/api/v1/users/me/deactivate", 409);
        mvc.perform(get("/api/v1/users/me").with(as(u))).andExpect(jsonPath("$.status").value("DEACTIVATED"));
        doPost(u, "/api/v1/users/me/reactivate", 200);
        mvc.perform(get("/api/v1/users/me").with(as(u))).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void erasureGracePeriodCancelAndExecution() throws Exception {
        UUID cancelled = UUID.randomUUID(), erased = UUID.randomUUID();

        // cancel path
        doPost(cancelled, "/api/v1/users/me/data-requests/erasure", 202);
        doPost(cancelled, "/api/v1/users/me/data-requests/erasure", 409);
        mvc.perform(get("/api/v1/users/me").with(as(cancelled))).andExpect(jsonPath("$.status").value("PENDING_DELETION"));
        mvc.perform(delete("/api/v1/users/me/data-requests/erasure").with(as(cancelled))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me").with(as(cancelled))).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(delete("/api/v1/users/me/data-requests/erasure").with(as(cancelled))).andExpect(status().isNotFound());

        // execution path: nothing happens inside the grace period, then the sweep erases
        doPut(erased, "/api/v1/users/me/contact", "{\"email\":\"gone@example.in\",\"phone\":\"+919811122233\"}", 200);
        doPost(erased, "/api/v1/users/me/data-requests/erasure", 202);
        long deletedBefore = events("pj.user.account-deleted.v1");
        assertThat(service.processDueErasures(Instant.now().plus(Duration.ofDays(29)))).isZero();
        assertThat(service.processDueErasures(Instant.now().plus(Duration.ofDays(31)))).isEqualTo(1);
        assertThat(events("pj.user.account-deleted.v1")).isEqualTo(deletedBefore + 1);
        assertThat(events("pj.user.erasure-requested.v1")).isGreaterThanOrEqualTo(2);
        mvc.perform(get("/api/v1/users/me/data-requests").with(as(erased)))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void exportRequestIsSingleFlight() throws Exception {
        UUID u = UUID.randomUUID();
        doPost(u, "/api/v1/users/me/data-requests/export", 202);
        doPost(u, "/api/v1/users/me/data-requests/export", 409);
    }

    @Test
    void identityEventCreatesUserAndBadMessagesAreIgnored() throws Exception {
        UUID u = UUID.randomUUID();
        listener.handle("{\"eventType\":\"UserRegistered\",\"payload\":{\"userId\":\"" + u + "\"}}");
        listener.handle("{\"eventType\":\"UserRegistered\",\"payload\":{\"userId\":\"" + u + "\"}}"); // idempotent
        listener.handle("not json");
        listener.handle("{\"payload\":{}}");
        mvc.perform(get("/api/v1/users/me").with(as(u))).andExpect(status().isOk());
        assertThat(UserService.mask("+919876543210")).isEqualTo("+91******3210");
        assertThat(UserService.mask("+91123")).isEqualTo("******");
    }
}
