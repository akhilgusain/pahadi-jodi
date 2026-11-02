package in.pahadijodi.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.moderation.domain.Enums.ContentType;
import in.pahadijodi.moderation.domain.ModerationCase;
import in.pahadijodi.moderation.domain.ModerationCaseRepository;
import in.pahadijodi.moderation.event.EventConsumers;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Drives the Kafka listeners' code path directly (no broker); sample-rate 1.0 sends every photo to spot-check. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:moderation_events;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false", "pj.kafka.consumers.enabled=false",
        "pj.moderation.image.sample-rate=1.0"})
class EventHandlingTest {
    @Autowired EventConsumers consumers;
    @Autowired ModerationCaseRepository cases;
    @Autowired ObjectMapper json;

    private String envelope(String payloadJson) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"X\",\"payload\":" + payloadJson + "}";
    }

    private List<ModerationCase> casesOf(UUID user) {
        return cases.findAll().stream().filter(c -> user.equals(c.getSubjectUserId())).toList();
    }

    @Test
    void uploadedPhotosAreSampledButOtherKindsAreNot() {
        UUID u = UUID.randomUUID();
        consumers.media(envelope("{\"mediaId\":\"m-1\",\"userId\":\"" + u + "\",\"kind\":\"PHOTO\"}"));
        consumers.media(envelope("{\"mediaId\":\"m-2\",\"userId\":\"" + u + "\",\"kind\":\"DOCUMENT\"}"));
        var mine = casesOf(u);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getContentType()).isEqualTo(ContentType.IMAGE);
        assertThat(mine.get(0).getRefId()).isEqualTo("m-1");
        assertThat(mine.get(0).getLabels()).isEqualTo("SAMPLE_REVIEW");
    }

    @Test
    void chatMessagesWithTextAreChecked_andRedeliveryIsIdempotent() {
        UUID u = UUID.randomUUID();
        String evt = envelope("{\"messageId\":\"msg-9\",\"senderId\":\"" + u + "\",\"text\":\"you stupid bastard\"}");
        consumers.chat(evt);
        consumers.chat(evt);                                    // Kafka redelivery of the same event
        assertThat(casesOf(u)).hasSize(1);
        assertThat(casesOf(u).get(0).getDecision().name()).isEqualTo("BLOCK");

        consumers.chat(envelope("{\"messageId\":\"msg-10\",\"senderId\":\"" + u + "\"}"));          // no text: nothing to check
        assertThat(casesOf(u)).hasSize(1);
    }

    @Test
    void profileUpdatesCheckNameAndBio() {
        UUID u = UUID.randomUUID();
        consumers.profile(envelope("{\"profileId\":\"p-1\",\"userId\":\"" + u + "\",\"displayName\":\"Ravi\","
                + "\"bio\":\"invest in bitcoin with me, send money first\"}"));
        var mine = casesOf(u);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getRefId()).isEqualTo("p-1#bio");
        assertThat(mine.get(0).getLabels()).contains("SCAM");
    }

    @Test
    void malformedMessagesAreSwallowed() {
        consumers.chat("this is not json");
        consumers.media("{}");
        consumers.profile("{\"payload\":{\"userId\":\"not-a-uuid\",\"displayName\":\"x\"}}");
    }
}
