package in.pahadijodi.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.notification.config.NotificationProperties;
import in.pahadijodi.notification.config.NotificationProperties.*;
import in.pahadijodi.notification.domain.NotificationType;
import in.pahadijodi.notification.events.EventRouter;
import in.pahadijodi.notification.service.NotificationRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventRouterTest {
    static final Topics T = new Topics("t.registered", "t.interest-sent", "t.interest-accepted", "t.match", "t.chat",
            "t.payment", "t.sub-expired", "t.verified");
    final EventRouter router = new EventRouter(new ObjectMapper(),
            new NotificationProperties(T, new DailyCaps(6, 2, 1, 2), new QuietHours(22, 8), new Delivery(5, 30, 50), new Worker(true), new Fake(List.of())));

    static String env(String eventId, String occurredAt, String payload) {
        return "{\"eventId\":\"" + eventId + "\",\"occurredAt\":\"" + occurredAt + "\",\"payload\":" + payload + "}";
    }

    @Test
    void userRegisteredGivesWelcome() {
        UUID u = UUID.randomUUID();
        List<NotificationRequest> r = router.route("t.registered", env("e1", "2026-06-10T10:00:00Z", "{\"userId\":\"" + u + "\"}"));
        assertThat(r).hasSize(1);
        assertThat(r.get(0).userId()).isEqualTo(u);
        assertThat(r.get(0).type()).isEqualTo(NotificationType.WELCOME);
        assertThat(r.get(0).dedupeKey()).isEqualTo("welcome");
    }

    @Test
    void interestSentNotifiesRecipientAndAcceptedNotifiesOriginalSender() {
        UUID from = UUID.randomUUID(), to = UUID.randomUUID();
        String p = "{\"interestId\":\"i-1\",\"fromUserId\":\"" + from + "\",\"toUserId\":\"" + to + "\"}";
        var sent = router.route("t.interest-sent", env("e2", "2026-06-10T10:00:00Z", p));
        assertThat(sent).singleElement().satisfies(n -> {
            assertThat(n.userId()).isEqualTo(to);
            assertThat(n.type()).isEqualTo(NotificationType.INTEREST_RECEIVED);
            assertThat(n.dedupeKey()).isEqualTo("interest-received:i-1");
        });
        var accepted = router.route("t.interest-accepted", env("e3", "2026-06-10T10:00:00Z", p));
        assertThat(accepted).singleElement().satisfies(n -> {
            assertThat(n.userId()).isEqualTo(from);
            assertThat(n.type()).isEqualTo(NotificationType.INTEREST_ACCEPTED);
        });
    }

    @Test
    void matchNotifiesBothUsersWithSameDedupeKey() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var r = router.route("t.match", env("e4", "2026-06-10T10:00:00Z", "{\"matchId\":\"m1\",\"userAId\":\"" + a + "\",\"userBId\":\"" + b + "\"}"));
        assertThat(r).extracting(NotificationRequest::userId).containsExactlyInAnyOrder(a, b);
        assertThat(r).extracting(NotificationRequest::dedupeKey).containsOnly("match:m1");
    }

    @Test
    void chatMessagesAreCollapsedPerConversationPerTenMinutes() {
        UUID to = UUID.randomUUID(), conv = UUID.randomUUID();
        String p = "{\"conversationId\":\"" + conv + "\",\"senderUserId\":\"" + UUID.randomUUID() + "\",\"recipientUserId\":\"" + to + "\"}";
        String k1 = router.route("t.chat", env("a", "2026-06-10T10:00:10Z", p)).get(0).dedupeKey();
        String k2 = router.route("t.chat", env("b", "2026-06-10T10:04:00Z", p)).get(0).dedupeKey();
        String k3 = router.route("t.chat", env("c", "2026-06-10T10:11:00Z", p)).get(0).dedupeKey();
        assertThat(k1).isEqualTo(k2).isNotEqualTo(k3);
        assertThat(router.route("t.chat", env("a", "2026-06-10T10:00:10Z", p)).get(0).data()).containsEntry("deepLink", "/chat/" + conv);
    }

    @Test
    void paymentFormatsAmount() {
        UUID u = UUID.randomUUID();
        var r = router.route("t.payment", env("e5", "2026-06-10T10:00:00Z",
                "{\"paymentId\":\"p1\",\"userId\":\"" + u + "\",\"amountMinor\":49900,\"currency\":\"INR\"}"));
        assertThat(r.get(0).params()).containsEntry("amount", "₹499.00");
        var usd = router.route("t.payment", env("e6", "2026-06-10T10:00:00Z", "{\"paymentId\":\"p2\",\"userId\":\"" + u + "\",\"amountMinor\":1205,\"currency\":\"usd\"}"));
        assertThat(usd.get(0).params()).containsEntry("amount", "USD 12.05");
    }

    @Test
    void subscriptionExpiredAndProfileVerified() {
        UUID u = UUID.randomUUID();
        assertThat(router.route("t.sub-expired", env("e7", "2026-06-10T10:00:00Z", "{\"userId\":\"" + u + "\",\"subscriptionId\":\"s1\"}")))
                .singleElement().satisfies(n -> assertThat(n.type()).isEqualTo(NotificationType.SUBSCRIPTION_EXPIRED));
        assertThat(router.route("t.verified", env("e8", "2026-06-10T10:00:00Z", "{\"userId\":\"" + u + "\",\"verificationType\":\"AADHAAR\"}")))
                .singleElement().satisfies(n -> assertThat(n.type()).isEqualTo(NotificationType.PROFILE_VERIFIED));
    }

    @Test
    void malformedIncompleteAndUnknownEventsProduceNothing() {
        assertThat(router.route("t.registered", "not json")).isEmpty();
        assertThat(router.route("t.registered", "[]")).isEmpty();
        assertThat(router.route("t.registered", env("e", "2026-06-10T10:00:00Z", "{}"))).isEmpty();
        assertThat(router.route("t.registered", env("e", "2026-06-10T10:00:00Z", "{\"userId\":\"nope\"}"))).isEmpty();
        assertThat(router.route("t.match", env("e", "2026-06-10T10:00:00Z", "{\"matchId\":\"m\"}"))).isEmpty();
        assertThat(router.route("some.other.topic", env("e", "2026-06-10T10:00:00Z", "{\"userId\":\"" + UUID.randomUUID() + "\"}"))).isEmpty();
    }
}
