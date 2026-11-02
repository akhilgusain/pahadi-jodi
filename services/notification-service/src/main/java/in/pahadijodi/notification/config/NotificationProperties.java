package in.pahadijodi.notification.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "pj.notification")
public record NotificationProperties(
        @DefaultValue Topics topics,
        @DefaultValue DailyCaps dailyCaps,
        @DefaultValue QuietHours quietHours,
        @DefaultValue Delivery delivery,
        @DefaultValue Worker worker,
        @DefaultValue Fake fake) {

    public record Topics(@DefaultValue("pj.identity.user-registered.v1") String userRegistered,
                         @DefaultValue("pj.interest.sent.v1") String interestSent,
                         @DefaultValue("pj.interest.accepted.v1") String interestAccepted,
                         @DefaultValue("pj.match.created.v1") String matchCreated,
                         @DefaultValue("pj.chat.message-sent.v1") String chatMessageSent,
                         @DefaultValue("pj.payment.captured.v1") String paymentCaptured,
                         @DefaultValue("pj.subscription.expired.v1") String subscriptionExpired,
                         @DefaultValue("pj.profile.verified.v1") String profileVerified) {}

    public record DailyCaps(@DefaultValue("6") int push, @DefaultValue("2") int email,
                            @DefaultValue("1") int sms, @DefaultValue("2") int whatsapp) {}

    public record QuietHours(@DefaultValue("22") int startHour, @DefaultValue("8") int endHour) {}

    public record Delivery(@DefaultValue("5") int maxAttempts, @DefaultValue("30") long backoffSeconds,
                           @DefaultValue("50") int batchSize) {}

    public record Worker(@DefaultValue("true") boolean enabled) {}

    /** Fake adapters fail (retryable) for these addresses: lets tests and demos exercise retry/dead-letter. */
    public record Fake(@DefaultValue List<String> failAddresses) {}
}
