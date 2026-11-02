package in.pahadijodi.notification.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.notification.config.NotificationProperties;
import in.pahadijodi.notification.domain.NotificationType;
import in.pahadijodi.notification.service.NotificationRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps a domain event (topic + envelope JSON) to zero or more notification requests. Pure and defensive: malformed
 * or incomplete events produce nothing (and a log line) instead of blocking the consumer on a poison message.
 * Field names follow docs/05 (fromUserId/toUserId, userAId/userBId, senderUserId/recipientUserId, ...).
 */
@Component
public class EventRouter {
    private static final Logger log = LoggerFactory.getLogger(EventRouter.class);
    private static final long CHAT_DEDUPE_WINDOW_SECONDS = 600;

    private final ObjectMapper mapper;
    private final NotificationProperties.Topics topics;

    public EventRouter(ObjectMapper mapper, NotificationProperties props) {
        this.mapper = mapper;
        this.topics = props.topics();
    }

    public List<NotificationRequest> route(String topic, String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            log.warn("Skipping unparsable event on {}: {}", topic, e.toString());
            return List.of();
        }
        if (root == null || !root.isObject()) return List.of();
        JsonNode p = root.has("payload") ? root.path("payload") : root;
        String eventId = text(root, "eventId");
        if (eventId == null) eventId = UUID.randomUUID().toString();

        List<NotificationRequest> out = new ArrayList<>();
        if (topic.equals(topics.userRegistered())) {
            UUID user = uuid(p, "userId", "accountId");
            if (user != null) out.add(req(user, NotificationType.WELCOME, "welcome", Map.of(), Map.of()));
        } else if (topic.equals(topics.interestSent())) {
            UUID to = uuid(p, "toUserId", "recipientUserId");
            if (to != null) out.add(req(to, NotificationType.INTEREST_RECEIVED, "interest-received:" + first(text(p, "interestId"), eventId),
                    Map.of(), Map.of("interestId", nz(text(p, "interestId")))));
        } else if (topic.equals(topics.interestAccepted())) {
            UUID original = uuid(p, "fromUserId", "senderUserId");    // the member whose interest was accepted
            if (original != null) out.add(req(original, NotificationType.INTEREST_ACCEPTED, "interest-accepted:" + first(text(p, "interestId"), eventId),
                    Map.of(), Map.of("interestId", nz(text(p, "interestId")))));
        } else if (topic.equals(topics.matchCreated())) {
            String key = "match:" + first(text(p, "matchId"), eventId);
            for (UUID u : new UUID[]{uuid(p, "userAId", "userA"), uuid(p, "userBId", "userB")}) {
                if (u != null) out.add(req(u, NotificationType.MATCH_CREATED, key, Map.of(), Map.of("matchId", nz(text(p, "matchId")))));
            }
        } else if (topic.equals(topics.chatMessageSent())) {
            UUID to = uuid(p, "recipientUserId", "recipientId");
            String conv = text(p, "conversationId");
            if (to != null && conv != null) {
                long bucket = occurredAt(root).getEpochSecond() / CHAT_DEDUPE_WINDOW_SECONDS;   // at most one ping per conversation per 10 min
                out.add(req(to, NotificationType.NEW_MESSAGE, "msg:" + conv + ":" + bucket, Map.of(),
                        Map.of("conversationId", conv, "deepLink", "/chat/" + conv)));
            }
        } else if (topic.equals(topics.paymentCaptured())) {
            UUID user = uuid(p, "userId");
            if (user != null) out.add(req(user, NotificationType.PAYMENT_SUCCESS,
                    "payment:" + first(text(p, "paymentId"), first(text(p, "orderId"), eventId)),
                    Map.of("amount", money(p.path("amountMinor").asLong(0), text(p, "currency"))), Map.of()));
        } else if (topic.equals(topics.subscriptionExpired())) {
            UUID user = uuid(p, "userId");
            if (user != null) out.add(req(user, NotificationType.SUBSCRIPTION_EXPIRED,
                    "sub-expired:" + first(text(p, "subscriptionId"), eventId), Map.of(), Map.of()));
        } else if (topic.equals(topics.profileVerified())) {
            UUID user = uuid(p, "userId");
            if (user != null) out.add(req(user, NotificationType.PROFILE_VERIFIED,
                    "verified:" + first(text(p, "verificationType"), first(text(p, "level"), "any")) + ":" + first(text(p, "verifiedAt"), eventId),
                    Map.of(), Map.of()));
        } else {
            log.debug("No routing for topic {}", topic);
        }
        return out;
    }

    static String money(long minor, String currency) {
        String cur = currency == null ? "INR" : currency.toUpperCase();
        String amount = String.format("%d.%02d", minor / 100, Math.abs(minor % 100));
        return ("INR".equals(cur) ? "₹" : cur + " ") + amount;
    }

    private static NotificationRequest req(UUID user, NotificationType type, String dedupe, Map<String, String> params, Map<String, String> data) {
        Map<String, String> d = new LinkedHashMap<>(data);
        d.values().removeIf(String::isEmpty);
        return new NotificationRequest(user, type, dedupe, params, d);
    }

    private static Instant occurredAt(JsonNode root) {
        try { return Instant.parse(root.path("occurredAt").asText()); } catch (Exception e) { return Instant.now(); }
    }

    private static String text(JsonNode n, String name) {
        JsonNode v = n.path(name);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static UUID uuid(JsonNode n, String... names) {
        for (String name : names) {
            String v = text(n, name);
            if (v != null) {
                try { return UUID.fromString(v); } catch (IllegalArgumentException ignored) { /* try next name */ }
            }
        }
        return null;
    }

    private static String first(String a, String b) { return a != null ? a : b; }
    private static String nz(String s) { return s == null ? "" : s; }
}
