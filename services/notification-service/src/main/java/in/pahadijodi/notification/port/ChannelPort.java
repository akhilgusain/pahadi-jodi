package in.pahadijodi.notification.port;

import in.pahadijodi.notification.domain.Channel;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One adapter per external channel: EMAIL (SES), SMS (MSG91/SNS with DLT templates), PUSH (FCM/APNs),
 * WHATSAPP (Business API templates). Only fake adapters exist so far.
 */
public interface ChannelPort {
    Channel channel();

    SendResult send(Message message);

    /** @param addresses email / phone / device tokens depending on the channel. */
    record Message(UUID notificationId, UUID userId, Channel channel, String title, String body,
                   Map<String, String> data, List<String> addresses) {}

    /** @param invalidAddresses addresses the provider rejected permanently (e.g. unregistered push tokens). */
    record SendResult(boolean success, boolean retryable, String providerRef, String error, List<String> invalidAddresses) {
        public static SendResult ok(String providerRef, List<String> invalid) { return new SendResult(true, false, providerRef, null, invalid); }
        public static SendResult retryable(String error) { return new SendResult(false, true, null, error, List.of()); }
        public static SendResult permanent(String error, List<String> invalid) { return new SendResult(false, false, null, error, invalid); }
    }
}
