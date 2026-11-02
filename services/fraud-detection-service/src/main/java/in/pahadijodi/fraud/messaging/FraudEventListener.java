package in.pahadijodi.fraud.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.fraud.domain.FraudSignal.Type;
import in.pahadijodi.fraud.service.FraudService;
import in.pahadijodi.fraud.service.FraudService.SignalCommand;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Turns domain events from other services into fraud signals. Parsing is defensive: a bad message is logged and skipped. */
@Component
public class FraudEventListener {
    private static final Logger log = LoggerFactory.getLogger(FraudEventListener.class);
    private final FraudService service;
    private final ObjectMapper json;

    public FraudEventListener(FraudService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @KafkaListener(topics = {"pj.identity.user-registered.v1", "pj.profile.updated.v1", "pj.chat.message-sent.v1"},
            autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void onCoreEvents(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        handle(topic, message);
    }

    @KafkaListener(topicPattern = "pj\\.(payment|media)\\..*", autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void onPaymentAndMedia(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        handle(topic, message);
    }

    /** Package-visible for tests. */
    public void handle(String topic, String message) {
        try {
            JsonNode env = json.readTree(message);
            JsonNode p = env.has("payload") ? env.get("payload") : env;
            String eventType = text(env, "eventType");
            SignalCommand cmd = switch (topic) {
                case "pj.identity.user-registered.v1" -> cmd(p, "userId", Type.REGISTERED, null, null, topic);
                case "pj.profile.updated.v1" -> cmd(p, "userId", Type.PROFILE_UPDATED, null, text(p, "bio"), topic);
                case "pj.chat.message-sent.v1" -> chat(p, topic);
                default -> topic.startsWith("pj.payment.") ? payment(p, topic, eventType)
                        : topic.startsWith("pj.media.") ? media(p, topic) : null;
            };
            if (cmd != null) service.ingest(cmd);
        } catch (Exception e) {
            log.warn("Skipping unprocessable event on {}: {}", topic, e.toString());
        }
    }

    private SignalCommand cmd(JsonNode p, String idField, Type type, String chat, String profileText, String trigger) {
        UUID user = uuid(p, idField);
        if (user == null) return null;
        return new SignalCommand(user, type, text(p, "deviceHash"), text(p, "ip"), text(p, "userAgent"),
                text(p, "emailDomain"), null, null, chat, profileText, null, trigger);
    }

    private SignalCommand chat(JsonNode p, String topic) {
        UUID sender = uuid(p, "senderId");
        if (sender == null) sender = uuid(p, "userId");
        if (sender == null) return null;
        String body = text(p, "text");
        if (body == null) body = text(p, "preview");
        return new SignalCommand(sender, Type.CHAT_MESSAGE, null, null, null, null, null, uuid(p, "recipientId"), body, null, null, topic);
    }

    private SignalCommand payment(JsonNode p, String topic, String eventType) {
        UUID user = uuid(p, "userId");
        if (user == null) return null;
        String t = (topic + " " + (eventType == null ? "" : eventType)).toLowerCase();
        Type type = t.contains("chargeback") || t.contains("dispute") ? Type.CHARGEBACK
                : t.contains("fail") ? Type.PAYMENT_FAILED : Type.PAYMENT_CAPTURED;
        return new SignalCommand(user, type, null, text(p, "ip"), null, null, null, null, null, null, null, topic);
    }

    private SignalCommand media(JsonNode p, String topic) {
        UUID user = uuid(p, "userId");
        String hex = text(p, "phash");
        if (user == null || hex == null) return null;   // only perceptual-hash carrying media events matter here
        return new SignalCommand(user, Type.PHOTO_UPLOADED, null, null, null, null, null, null, null, null,
                Long.parseUnsignedLong(hex, 16), topic);
    }

    private static UUID uuid(JsonNode n, String f) {
        String s = text(n, f);
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static String text(JsonNode n, String f) {
        return n != null && n.hasNonNull(f) ? n.get(f).asText() : null;
    }
}
