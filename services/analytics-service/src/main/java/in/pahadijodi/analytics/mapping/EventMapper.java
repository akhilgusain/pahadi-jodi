package in.pahadijodi.analytics.mapping;

import static in.pahadijodi.analytics.model.EventNames.*;

import com.fasterxml.jackson.databind.JsonNode;
import in.pahadijodi.analytics.model.FactRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turns any pj.* event into 0..2 fact rows. Classification is by keywords in the topic name, because the topics
 * are owned by other services and their naming differs slightly (pj.identity.user-registered.v1 vs
 * pj.identity.user.registered.v1, pj.matching.interest.sent.v1, ...). Payload field names are likewise matched
 * against a list of aliases. Unknown events are kept as OTHER (they still count towards active users).
 */
@Component
public class EventMapper {
    private static final String[] ACTOR = {"userId", "accountId", "fromUserId", "senderId", "senderUserId", "actorId",
            "viewerId", "viewerUserId", "payerUserId", "memberId", "buyerUserId"};
    private static final String[] TARGET = {"toUserId", "targetUserId", "recipientId", "recipientUserId", "receiverId",
            "profileUserId", "profileOwnerId", "viewedUserId", "ownerUserId"};
    private static final String[][] PAIRS = {{"userA", "userB"}, {"userIdA", "userIdB"}, {"user1", "user2"},
            {"memberA", "memberB"}, {"fromUserId", "toUserId"}, {"userId", "matchedUserId"}};

    private final Pseudonymizer pseudo;
    private final ZoneId zone;

    public EventMapper(Pseudonymizer pseudo, @Value("${pj.analytics.timezone:Asia/Kolkata}") String zone) {
        this.pseudo = pseudo;
        this.zone = ZoneId.of(zone);
    }

    /** Dead-letter and retry topics are operational noise. */
    public static boolean ignoredTopic(String topic) {
        String t = topic.toLowerCase(Locale.ROOT);
        return !t.startsWith("pj.") || t.contains(".dlq") || t.contains(".dlt") || t.contains(".retry");
    }

    public String classify(String topic) {
        String t = topic.toLowerCase(Locale.ROOT);
        if (t.contains("registered") && t.contains("user") && !t.contains("device")) return REGISTERED;
        if (t.contains("profile") && t.contains("viewed")) return PROFILE_VIEWED;
        if (t.contains("profile") && (t.contains("updated") || t.contains("created")) && !t.contains("verified")) return PROFILE_UPDATED;
        if (t.contains("interest") && t.contains("sent")) return INTEREST_SENT;
        if (t.contains("interest") && t.contains("accept")) return INTEREST_ACCEPTED;
        if (t.contains("match") && t.contains("created") && !t.contains("recommend")) return MATCH_CREATED;
        if (t.contains("message") && t.contains("sent")) return MESSAGE_SENT;
        if (t.contains("subscription") && t.contains("activated")) return SUBSCRIPTION_ACTIVATED;
        if (t.contains("payment") && t.contains("captured")) return PAYMENT_CAPTURED;
        return OTHER;
    }

    public List<FactRow> map(String topic, String eventId, Instant occurredAt, JsonNode payload) {
        String name = classify(topic);
        LocalDate day = occurredAt.atZone(zone).toLocalDate();
        List<FactRow> rows = new ArrayList<>(2);

        if (name.equals(MATCH_CREATED)) {
            String[] pair = pair(payload);
            if (pair != null) {
                rows.add(row(eventId, name, topic, pseudo.hash(pair[0]), pseudo.hash(pair[1]), occurredAt, day, payload));
                rows.add(row(eventId, name, topic, pseudo.hash(pair[1]), pseudo.hash(pair[0]), occurredAt, day, payload));
                return rows;
            }
        }
        String actor = first(payload, ACTOR), target = first(payload, TARGET);
        // profile views: the viewer is the actor, the profile owner the target
        if (name.equals(PROFILE_VIEWED) && actor != null && target == null) target = first(payload, "profileUserId", "userId");
        String ah = actor == null ? NO_ACTOR : pseudo.hash(actor);
        String th = target == null || target.equals(actor) ? null : pseudo.hash(target);
        String effective = name;
        if (name.equals(PROFILE_UPDATED) && payload.path("completeness").asInt(0) >= 80) {
            // a saved profile at >= 80% completeness is recorded as PROFILE_COMPLETE (and the funnel counts distinct users)
            effective = PROFILE_COMPLETE;
        }
        rows.add(row(eventId, effective, topic, ah, th, occurredAt, day, payload));
        return rows;
    }

    private FactRow row(String eventId, String name, String topic, String actorHash, String targetHash,
                        Instant at, LocalDate day, JsonNode p) {
        Long amount = null;
        String currency = null;
        if (name.equals(PAYMENT_CAPTURED) || name.equals(SUBSCRIPTION_ACTIVATED)) {
            amount = amountMinor(p);
            currency = (amount != null || p.has("currency")) ? p.path("currency").asText("INR").toUpperCase(Locale.ROOT) : null;
        }
        String plan = null;
        String planRaw = first(p, "planCode", "plan", "tier", "planId");
        if (planRaw != null) plan = planRaw.toUpperCase(Locale.ROOT);
        String community = text(p, "community"), district = text(p, "district");
        return new FactRow(eventId, name, topic, actorHash, targetHash, at, day, amount, currency, plan,
                community == null ? null : community.toUpperCase(Locale.ROOT), district == null ? null : district.toUpperCase(Locale.ROOT));
    }

    /** amountMinor / amountPaise are minor units; a bare "amount" is minor if integral, else major units (x100). */
    static Long amountMinor(JsonNode p) {
        for (String f : new String[]{"amountMinor", "amountPaise"}) if (p.hasNonNull(f)) return p.get(f).asLong();
        if (p.hasNonNull("amount")) {
            JsonNode a = p.get("amount");
            return a.isIntegralNumber() ? a.asLong() : Math.round(a.asDouble() * 100);
        }
        return null;
    }

    private static String[] pair(JsonNode p) {
        for (String[] pr : PAIRS) {
            String a = text(p, pr[0]), b = text(p, pr[1]);
            if (a != null && b != null && !a.equals(b)) return new String[]{a, b};
        }
        for (String arr : new String[]{"participants", "userIds", "members"}) {
            JsonNode n = p.path(arr);
            if (n.isArray() && n.size() >= 2 && n.get(0).isTextual() && n.get(1).isTextual()) {
                return new String[]{n.get(0).asText(), n.get(1).asText()};
            }
        }
        return null;
    }

    private static String first(JsonNode p, String... fields) {
        for (String f : fields) {
            String v = text(p, f);
            if (v != null) return v;
        }
        return null;
    }

    private static String text(JsonNode p, String f) {
        JsonNode n = p.get(f);
        return n == null || n.isNull() || !n.isValueNode() || n.asText().isBlank() ? null : n.asText();
    }
}
