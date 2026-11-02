package in.pahadijodi.moderation.service;

import com.fasterxml.jackson.databind.JsonNode;
import in.pahadijodi.moderation.domain.Enums.ContentType;
import in.pahadijodi.moderation.domain.ProcessedEvent;
import in.pahadijodi.moderation.domain.ProcessedEventRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent processing of upstream events (at-least-once delivery): eventId is recorded in the same transaction. */
@Service
public class EventHandler {
    private static final Logger log = LoggerFactory.getLogger(EventHandler.class);
    private static final String[] TEXT_FIELDS = {"text", "body", "content", "message"};
    private static final String[] SENDER_FIELDS = {"senderId", "fromUserId", "userId"};
    private static final String[] MESSAGE_ID_FIELDS = {"messageId", "id"};

    private final ModerationService moderation;
    private final ProcessedEventRepository processed;

    public EventHandler(ModerationService moderation, ProcessedEventRepository processed) {
        this.moderation = moderation;
        this.processed = processed;
    }

    /** pj.media.uploaded.v1 -> photo spot-checks. */
    @Transactional
    public void onMediaUploaded(JsonNode env) {
        if (seen(env)) return;
        JsonNode p = env.path("payload");
        if (!"PHOTO".equals(p.path("kind").asText())) return;
        UUID user = uuid(p, "userId");
        if (user == null || !p.hasNonNull("mediaId")) return;
        moderation.check(user, ContentType.IMAGE, null, p.get("mediaId").asText(), "EVENT");
    }

    /** pj.chat.message-sent.v1 -> only if the event carries message text (otherwise chat-service calls /check inline). */
    @Transactional
    public void onChatMessage(JsonNode env) {
        if (seen(env)) return;
        JsonNode p = env.path("payload");
        String text = first(p, TEXT_FIELDS);
        UUID sender = null;
        for (String f : SENDER_FIELDS) if (sender == null) sender = uuid(p, f);
        if (text == null || sender == null) {
            log.debug("chat event without text/sender; skipped");
            return;
        }
        String ref = first(p, MESSAGE_ID_FIELDS);
        moderation.check(sender, ContentType.TEXT, text, ref, "EVENT");
    }

    /** pj.profile.updated.v1 -> display name and (when present) bio. */
    @Transactional
    public void onProfileUpdated(JsonNode env) {
        if (seen(env)) return;
        JsonNode p = env.path("payload");
        UUID user = uuid(p, "userId");
        String profileId = p.path("profileId").asText("");
        if (user == null) return;
        if (p.hasNonNull("displayName")) moderation.check(user, ContentType.BIO, p.get("displayName").asText(), profileId + "#name", "EVENT");
        if (p.hasNonNull("bio")) moderation.check(user, ContentType.BIO, p.get("bio").asText(), profileId + "#bio", "EVENT");
    }

    /** true when this envelope was already processed (and records it otherwise). */
    private boolean seen(JsonNode env) {
        String id = env.path("eventId").asText(null);
        if (id == null) return false;           // no id: cannot dedupe, process anyway
        if (processed.existsById(id)) return true;
        processed.save(new ProcessedEvent(id));
        return false;
    }

    private static String first(JsonNode p, String[] fields) {
        for (String f : fields) if (p.hasNonNull(f) && !p.get(f).asText().isBlank()) return p.get(f).asText();
        return null;
    }

    private static UUID uuid(JsonNode p, String field) {
        try {
            return p.hasNonNull(field) ? UUID.fromString(p.get(field).asText()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
