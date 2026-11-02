package in.pahadijodi.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.outbox.OutboxRepository;
import in.pahadijodi.media.domain.MatchLink;
import in.pahadijodi.media.domain.MatchLinkRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.imageio.ImageIO;
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
        "spring.datasource.url=jdbc:h2:mem:media;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false", "pj.kafka.consumers.enabled=false",
        "pj.media.storage-dir=target/test-media"})
class MediaApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxRepository outbox;
    @Autowired MatchLinkRepository matches;

    // ---- helpers -----------------------------------------------------------------------------------

    static RequestPostProcessor as(UUID user, String... roles) {
        var p = jwt().jwt(j -> j.subject(user.toString()));
        var auths = new SimpleGrantedAuthority[roles.length];
        for (int i = 0; i < roles.length; i++) auths[i] = new SimpleGrantedAuthority("ROLE_" + roles[i]);
        return roles.length == 0 ? p : p.authorities(auths);
    }

    /** 256x256 image made of an 8x8 grid of 32px cells, white where bits[i] is true. */
    static byte[] grid(boolean[] bits, String fmt) throws Exception {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int i = 0; i < 64; i++) {
            g.setColor(bits[i] ? Color.WHITE : Color.BLACK);
            g.fillRect((i % 8) * 32, (i / 8) * 32, 32, 32);
        }
        g.dispose();
        return encode(img, fmt);
    }

    static byte[] encode(BufferedImage img, String fmt) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, fmt, out);
        return out.toByteArray();
    }

    static boolean[] checker() {
        boolean[] b = new boolean[64];
        for (int i = 0; i < 64; i++) b[i] = ((i / 8) + (i % 8)) % 2 == 0;
        return b;
    }

    /** Flips k white cells to black and k black cells to white (keeps the mean brightness). */
    static boolean[] flipped(boolean[] base, int k) {
        boolean[] b = base.clone();
        int w = 0, bl = 0;
        for (int i = 0; i < 64; i++) {
            if (base[i] && w < k) { b[i] = false; w++; }
            else if (!base[i] && bl < k) { b[i] = true; bl++; }
        }
        return b;
    }

    static boolean[] inverted(boolean[] base) {
        boolean[] b = new boolean[64];
        for (int i = 0; i < 64; i++) b[i] = !base[i];
        return b;
    }

    JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    /** declare + PUT + complete; returns the completed asset JSON. */
    JsonNode upload(UUID user, String kind, String contentType, byte[] data) throws Exception {
        var d = body(mvc.perform(post("/api/v1/media/uploads").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"" + kind + "\",\"contentType\":\"" + contentType + "\",\"sizeBytes\":" + data.length + "}"))
                .andExpect(status().isCreated()));
        String id = d.get("id").asText();
        mvc.perform(put("/api/v1/media/uploads/" + id + "/content").with(as(user)).contentType(contentType).content(data))
                .andExpect(status().isNoContent());
        return body(mvc.perform(post("/api/v1/media/uploads/" + id + "/complete").with(as(user))).andExpect(status().isOk()));
    }

    boolean outboxHas(String topic, String fragment) {
        return outbox.findAll().stream().anyMatch(e -> e.getTopic().equals(topic) && e.getEnvelope().contains(fragment));
    }

    // ---- upload pipeline ----------------------------------------------------------------------------

    @Test
    void photoPipelineProducesSignedUrlsAndStripsToJpeg() throws Exception {
        UUID u = UUID.randomUUID();
        JsonNode a = upload(u, "PHOTO", "image/png", grid(checker(), "png"));
        assertThat(a.get("status").asText()).isEqualTo("READY");
        assertThat(a.get("primary").asBoolean()).isTrue();          // first photo becomes the primary one
        assertThat(a.get("contentType").asText()).isEqualTo("image/jpeg");
        assertThat(a.get("width").asInt()).isEqualTo(256);
        assertThat(outboxHas("pj.media.uploaded.v1", u.toString())).isTrue();
        assertThat(outboxHas("pj.media.ready.v1", a.get("id").asText())).isTrue();

        String id = a.get("id").asText();
        JsonNode url = body(mvc.perform(get("/api/v1/media/" + id + "/url").with(as(u))).andExpect(status().isOk()));
        String link = url.get("url").asText();

        // the signed link needs no bearer token and serves a JPEG
        byte[] bytes = mvc.perform(get(link)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(bytes[0] & 0xff).isEqualTo(0xFF);
        assertThat(bytes[1] & 0xff).isEqualTo(0xD8);

        mvc.perform(get(link.replaceAll("sig=.{6}", "sig=000000"))).andExpect(status().isForbidden());
        mvc.perform(get(link.replaceAll("exp=\\d+", "exp=1"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/media/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void largeImagesAreResizedAndThumbnailed() throws Exception {
        UUID u = UUID.randomUUID();
        BufferedImage big = new BufferedImage(3000, 1500, BufferedImage.TYPE_INT_RGB);
        JsonNode a = upload(u, "PHOTO", "image/jpeg", encode(big, "jpg"));
        assertThat(a.get("width").asInt()).isEqualTo(2048);
        assertThat(a.get("height").asInt()).isEqualTo(1024);
        String id = a.get("id").asText();
        String thumbLink = body(mvc.perform(get("/api/v1/media/" + id + "/url?variant=thumb").with(as(u)))).get("url").asText();
        assertThat(thumbLink).contains("v=thumb");
        byte[] thumb = mvc.perform(get(thumbLink)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        BufferedImage t = ImageIO.read(new java.io.ByteArrayInputStream(thumb));
        assertThat(Math.max(t.getWidth(), t.getHeight())).isEqualTo(320);
    }

    @Test
    void declareValidationAndBadUploads() throws Exception {
        UUID u = UUID.randomUUID();
        mvc.perform(post("/api/v1/media/uploads").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"PHOTO\",\"contentType\":\"application/zip\",\"sizeBytes\":100}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post("/api/v1/media/uploads").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"PHOTO\",\"contentType\":\"image/png\",\"sizeBytes\":999999999}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        // declared size must match the body
        String id = body(mvc.perform(post("/api/v1/media/uploads").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"PHOTO\",\"contentType\":\"image/png\",\"sizeBytes\":50}"))).get("id").asText();
        mvc.perform(put("/api/v1/media/uploads/" + id + "/content").with(as(u)).contentType("image/png").content(new byte[10]))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SIZE_MISMATCH"));
        // someone else's upload id looks like it does not exist
        mvc.perform(put("/api/v1/media/uploads/" + id + "/content").with(as(UUID.randomUUID())).contentType("image/png").content(new byte[50]))
                .andExpect(status().isNotFound());

        // garbage declared as an image is rejected by the pipeline
        JsonNode bad = upload(u, "PHOTO", "image/png", "definitely not an image".getBytes(StandardCharsets.UTF_8));
        assertThat(bad.get("status").asText()).isEqualTo("REJECTED");
        assertThat(bad.get("rejectReason").asText()).isEqualTo("INVALID_IMAGE");
    }

    @Test
    void malwareIsRejectedAndNotStored() throws Exception {
        UUID u = UUID.randomUUID();
        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        JsonNode a = upload(u, "DOCUMENT", "application/pdf", ("%PDF-1.4 " + eicar).getBytes(StandardCharsets.ISO_8859_1));
        assertThat(a.get("status").asText()).isEqualTo("REJECTED");
        assertThat(a.get("rejectReason").asText()).isEqualTo("MALWARE_DETECTED");
        mvc.perform(get("/api/v1/media/" + a.get("id").asText() + "/url").with(as(u))).andExpect(status().isNotFound());
    }

    // ---- gallery & privacy -----------------------------------------------------------------------------

    @Test
    void galleryOrderPrimaryAndDelete() throws Exception {
        UUID u = UUID.randomUUID();
        String p1 = upload(u, "PHOTO", "image/png", grid(checker(), "png")).get("id").asText();
        JsonNode second = upload(u, "PHOTO", "image/png", grid(inverted(checker()), "png"));
        String p2 = second.get("id").asText();
        assertThat(second.get("primary").asBoolean()).isFalse();
        assertThat(second.get("position").asInt()).isEqualTo(1);

        mvc.perform(put("/api/v1/media/order").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + p2 + "\",\"" + p1 + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(p2));
        mvc.perform(put("/api/v1/media/order").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + p2 + "\"]}")).andExpect(status().isUnprocessableEntity());

        mvc.perform(put("/api/v1/media/" + p2 + "/primary").with(as(u))).andExpect(jsonPath("$.primary").value(true));
        // the primary photo cannot be hidden entirely
        mvc.perform(patch("/api/v1/media/" + p2).with(as(u)).contentType(MediaType.APPLICATION_JSON).content("{\"privacy\":\"PRIVATE\"}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(delete("/api/v1/media/" + p2).with(as(u))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/media/me").with(as(u))).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(p1)).andExpect(jsonPath("$[0].primary").value(true));
    }

    @Test
    void privacyLevelsAreEnforcedForOtherViewers() throws Exception {
        UUID owner = UUID.randomUUID(), viewer = UUID.randomUUID();
        String id = upload(owner, "PHOTO", "image/png", grid(checker(), "png")).get("id").asText();
        String path = "/api/v1/media/" + id + "/url";

        mvc.perform(get(path).with(as(viewer))).andExpect(status().isOk());                       // PUBLIC (default)

        mvc.perform(patch("/api/v1/media/" + id).with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"privacy\":\"MATCHES_ONLY\"}"));
        mvc.perform(get(path).with(as(viewer))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MEDIA_NOT_VISIBLE"));
        matches.save(new MatchLink(viewer, owner));
        mvc.perform(get(path).with(as(viewer))).andExpect(status().isOk());                       // after a mutual match

        UUID stranger = UUID.randomUUID();
        mvc.perform(patch("/api/v1/media/" + id).with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"privacy\":\"ON_REQUEST\"}"));
        mvc.perform(get(path).with(as(stranger))).andExpect(status().isForbidden());
        String reqId = body(mvc.perform(post("/api/v1/media/access-requests").with(as(stranger)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":\"" + owner + "\"}")).andExpect(status().isCreated())).get("id").asText();
        mvc.perform(get(path).with(as(stranger))).andExpect(status().isForbidden());              // still pending
        mvc.perform(get("/api/v1/media/access-requests").with(as(owner))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(put("/api/v1/media/access-requests/" + reqId).with(as(stranger)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"GRANT\"}")).andExpect(status().isNotFound());           // only the owner may decide
        mvc.perform(put("/api/v1/media/access-requests/" + reqId).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"GRANT\"}")).andExpect(jsonPath("$.status").value("GRANTED"));
        mvc.perform(get(path).with(as(stranger))).andExpect(status().isOk());
    }

    @Test
    void documentsAndSelfiesAreVisibleOnlyToOwnerAndVerifiers() throws Exception {
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        String id = upload(owner, "DOCUMENT", "application/pdf", "%PDF-1.4 hello".getBytes(StandardCharsets.US_ASCII)).get("id").asText();
        String path = "/api/v1/media/" + id + "/url";
        mvc.perform(get(path).with(as(owner))).andExpect(status().isOk());
        mvc.perform(get(path).with(as(other))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(as(other, "VERIFIER"))).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/media/" + id).with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"privacy\":\"PUBLIC\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- verification ------------------------------------------------------------------------------------

    JsonNode pan(UUID u, String pan, int expectedStatus) throws Exception {
        return body(mvc.perform(post("/api/v1/verification/pan").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pan\":\"" + pan + "\"}")).andExpect(status().is(expectedStatus)));
    }

    JsonNode check(JsonNode status, String type) {
        for (JsonNode c : status.get("checks")) if (c.get("type").asText().equals(type)) return c;
        throw new AssertionError("no " + type + " check in " + status);
    }

    @Test
    void panVerificationRejectsBadFormatAndDuplicates() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        JsonNode bad = pan(a, "ABCDE1234F", 200);                                   // 4th letter must be P for individuals
        assertThat(check(bad, "PAN").get("status").asText()).isEqualTo("REJECTED");
        assertThat(check(bad, "PAN").get("reason").asText()).isEqualTo("PAN_FORMAT_INVALID");
        assertThat(bad.get("state").asText()).isEqualTo("REJECTED");

        JsonNode ok = pan(a, "ghkpl4321m", 200);                                    // retry allowed, case-insensitive
        assertThat(check(ok, "PAN").get("status").asText()).isEqualTo("VERIFIED");
        assertThat(check(ok, "PAN").get("maskedId").asText()).isEqualTo("XXXXXXXX321M");   // never the full number
        assertThat(ok.get("level").asText()).isEqualTo("ID_VERIFIED");
        assertThat(ok.get("state").asText()).isEqualTo("PENDING");                  // still needs the selfie
        assertThat(ok.toString()).doesNotContain("GHKPL4321M");
        assertThat(outboxHas("pj.profile.verified.v1", "ID_VERIFIED")).isTrue();

        pan(a, "GHKPL4321M", 409);                                                  // already verified
        JsonNode dup = pan(b, "GHKPL4321M", 200);                                   // same PAN, other account
        assertThat(check(dup, "PAN").get("reason").asText()).isEqualTo("DUPLICATE_ID");
        assertThat(dup.get("level").asText()).isEqualTo("NONE");
    }

    @Test
    void aadhaarConsentFlowStoresOnlyLast4() throws Exception {
        UUID u = UUID.randomUUID();
        mvc.perform(post("/api/v1/verification/aadhaar/complete").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reference\":\"x\",\"code\":\"123456\"}")).andExpect(status().isConflict());

        String ref = body(mvc.perform(post("/api/v1/verification/aadhaar/start").with(as(u))).andExpect(status().isCreated())).get("reference").asText();
        JsonNode wrong = body(mvc.perform(post("/api/v1/verification/aadhaar/complete").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reference\":\"" + ref + "\",\"code\":\"000000\"}")).andExpect(status().isOk()));
        assertThat(check(wrong, "AADHAAR").get("status").asText()).isEqualTo("REJECTED");

        String ref2 = body(mvc.perform(post("/api/v1/verification/aadhaar/start").with(as(u)))).get("reference").asText();
        JsonNode ok = body(mvc.perform(post("/api/v1/verification/aadhaar/complete").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reference\":\"" + ref2 + "\",\"code\":\"123456\"}")).andExpect(status().isOk()));
        assertThat(check(ok, "AADHAAR").get("status").asText()).isEqualTo("VERIFIED");
        assertThat(check(ok, "AADHAAR").get("maskedId").asText()).matches("XXXXXXXX\\d{4}");
        assertThat(ok.get("level").asText()).isEqualTo("ID_VERIFIED");
    }

    @Test
    void selfieMatchGivesBlueTickAfterId() throws Exception {
        UUID u = UUID.randomUUID();
        JsonNode selfieAsset;
        // no profile photo yet -> cannot start
        selfieAsset = upload(u, "SELFIE", "image/png", grid(checker(), "png"));
        mvc.perform(post("/api/v1/verification/selfie").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"selfieMediaId\":\"" + selfieAsset.get("id").asText() + "\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("NO_PROFILE_PHOTO"));

        upload(u, "PHOTO", "image/png", grid(checker(), "png"));
        JsonNode r = body(mvc.perform(post("/api/v1/verification/selfie").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"selfieMediaId\":\"" + selfieAsset.get("id").asText() + "\"}")).andExpect(status().isOk()));
        assertThat(check(r, "SELFIE").get("status").asText()).isEqualTo("VERIFIED");
        assertThat(r.get("level").asText()).isEqualTo("NONE");                     // selfie alone is not enough

        JsonNode both = pan(u, "BCDPE5678F", 200);
        assertThat(both.get("level").asText()).isEqualTo("BLUE_TICK");
        assertThat(both.get("state").asText()).isEqualTo("VERIFIED");
        assertThat(outboxHas("pj.profile.verified.v1", "BLUE_TICK")).isTrue();
    }

    @Test
    void selfieMismatchLivenessAndManualReview() throws Exception {
        UUID u = UUID.randomUUID();
        upload(u, "PHOTO", "image/png", grid(checker(), "png"));

        // a completely different image is rejected
        String different = upload(u, "SELFIE", "image/png", grid(inverted(checker()), "png")).get("id").asText();
        JsonNode r1 = body(mvc.perform(post("/api/v1/verification/selfie").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"selfieMediaId\":\"" + different + "\"}")).andExpect(status().isOk()));
        assertThat(check(r1, "SELFIE").get("reason").asText()).isEqualTo("FACE_MISMATCH");

        // a tiny image fails liveness
        BufferedImage tiny = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        String small = upload(u, "SELFIE", "image/png", encode(tiny, "png")).get("id").asText();
        JsonNode r2 = body(mvc.perform(post("/api/v1/verification/selfie").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"selfieMediaId\":\"" + small + "\"}")).andExpect(status().isOk()));
        assertThat(check(r2, "SELFIE").get("reason").asText()).isEqualTo("LIVENESS_FAILED");

        // a partial match lands in the manual review queue
        String near = upload(u, "SELFIE", "image/png", grid(flipped(checker(), 10), "png")).get("id").asText();
        JsonNode r3 = body(mvc.perform(post("/api/v1/verification/selfie").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"selfieMediaId\":\"" + near + "\"}")).andExpect(status().isOk()));
        assertThat(check(r3, "SELFIE").get("status").asText()).isEqualTo("MANUAL_REVIEW");
        String checkId = check(r3, "SELFIE").get("id").asText();

        mvc.perform(get("/api/v1/verification/admin/queue").with(as(u))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/verification/admin/queue").with(as(UUID.randomUUID(), "VERIFIER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + checkId + "')]").exists());
        mvc.perform(post("/api/v1/verification/admin/" + checkId + "/decision").with(as(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}")).andExpect(status().isForbidden());
        JsonNode approved = body(mvc.perform(post("/api/v1/verification/admin/" + checkId + "/decision").with(as(UUID.randomUUID(), "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}")).andExpect(status().isOk()));
        assertThat(check(approved, "SELFIE").get("status").asText()).isEqualTo("VERIFIED");
        // decided items leave the queue
        mvc.perform(post("/api/v1/verification/admin/" + checkId + "/decision").with(as(UUID.randomUUID(), "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}")).andExpect(status().isNotFound());
    }
}
