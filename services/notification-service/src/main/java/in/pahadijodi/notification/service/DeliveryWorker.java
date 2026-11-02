package in.pahadijodi.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.notification.config.NotificationProperties;
import in.pahadijodi.notification.domain.*;
import in.pahadijodi.notification.port.ChannelPort;
import in.pahadijodi.notification.port.ChannelPort.Message;
import in.pahadijodi.notification.port.ChannelPort.SendResult;
import in.pahadijodi.notification.port.ChannelRegistry;
import in.pahadijodi.notification.port.ContactDirectoryPort;
import in.pahadijodi.notification.port.ContactDirectoryPort.Contact;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends due PENDING deliveries. Failure handling: retryable error -> exponential backoff (base * 2^(attempt-1)) up to
 * max-attempts, then DEAD + dead_letter row. Permanent error -> DEAD immediately. Safe with several pods: the
 * delivery row has an optimistic-lock version, so only one worker wins each row.
 */
@Component
public class DeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final DeliveryLogRepository deliveries;
    private final NotificationRepository notifications;
    private final DeadLetterRepository deadLetters;
    private final DeviceTokenRepository devices;
    private final ContactDirectoryPort contacts;
    private final ChannelRegistry channels;
    private final NotificationProperties props;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final boolean enabled;

    public DeliveryWorker(DeliveryLogRepository deliveries, NotificationRepository notifications,
                          DeadLetterRepository deadLetters, DeviceTokenRepository devices, ContactDirectoryPort contacts,
                          ChannelRegistry channels, NotificationProperties props, TransactionTemplate tx,
                          ObjectMapper mapper, Clock clock, @Value("${pj.notification.worker.enabled:true}") boolean enabled) {
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.deadLetters = deadLetters;
        this.devices = devices;
        this.contacts = contacts;
        this.channels = channels;
        this.props = props;
        this.tx = tx;
        this.mapper = mapper;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${pj.notification.worker.interval-ms:2000}")
    public void tick() {
        if (enabled) runDue();
    }

    /** @return number of deliveries attempted. */
    public int runDue() {
        List<UUID> due = deliveries.findDueIds(DeliveryStatus.PENDING, clock.instant(), PageRequest.of(0, props.delivery().batchSize()));
        int attempted = 0;
        for (UUID id : due) {
            try {
                if (Boolean.TRUE.equals(tx.execute(s -> attempt(id)))) attempted++;
            } catch (OptimisticLockingFailureException e) {
                log.debug("delivery {} claimed by another worker", id);
            } catch (RuntimeException e) {
                log.warn("delivery {} failed unexpectedly: {}", id, e.toString());
            }
        }
        return attempted;
    }

    private boolean attempt(UUID deliveryId) {
        DeliveryLog d = deliveries.findById(deliveryId).orElse(null);
        if (d == null || d.getStatus() != DeliveryStatus.PENDING) return false;
        Notification n = notifications.findById(d.getNotificationId()).orElseThrow();
        ChannelPort port = channels.get(d.getChannel()).orElse(null);
        if (port == null) { d.suppress("NO_PROVIDER"); return true; }

        List<String> addresses = addressesFor(d);
        if (addresses.isEmpty()) {
            d.suppress(d.getChannel() == Channel.PUSH ? "NO_DEVICE" : "NO_ADDRESS");
            return true;
        }
        Map<String, String> data = new LinkedHashMap<>();
        try {
            if (n.getDataJson() != null) data.putAll(mapper.readValue(n.getDataJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}));
        } catch (Exception ignored) { /* data is optional */ }

        SendResult r;
        try {
            r = port.send(new Message(n.getId(), n.getUserId(), d.getChannel(), n.getTitle(), n.getBody(), data, addresses));
        } catch (RuntimeException e) {
            r = SendResult.retryable("adapter threw: " + e);
        }
        Instant now = clock.instant();
        if (d.getChannel() == Channel.PUSH && !r.invalidAddresses().isEmpty()) devices.deleteByTokenIn(r.invalidAddresses());   // prune dead tokens

        if (r.success()) {
            d.sent(r.providerRef(), now);
        } else if (r.retryable() && d.getAttempts() + 1 < props.delivery().maxAttempts()) {
            long delay = props.delivery().backoffSeconds() * (1L << d.getAttempts());   // attempts before this one
            d.retryLater(r.error(), now.plus(Duration.ofSeconds(delay)));
        } else {
            d.dead(r.error());
            deadLetters.save(new DeadLetter(d, "{\"title\":" + quote(n.getTitle()) + ",\"type\":\"" + n.getType() + "\"}", r.error(), now));
            log.warn("delivery {} for user {} on {} moved to dead letter: {}", d.getId(), d.getUserId(), d.getChannel(), r.error());
        }
        return true;
    }

    private List<String> addressesFor(DeliveryLog d) {
        return switch (d.getChannel()) {
            case PUSH -> devices.findByUserIdOrderByLastSeenAtDesc(d.getUserId()).stream().map(DeviceToken::getToken).toList();
            case EMAIL -> nonEmpty(contacts.lookup(d.getUserId()).email());
            case SMS, WHATSAPP -> nonEmpty(contacts.lookup(d.getUserId()).phone());
            case IN_APP -> List.of();
        };
    }

    private static List<String> nonEmpty(String v) { return v == null || v.isBlank() ? List.of() : List.of(v); }

    private String quote(String s) {
        try { return mapper.writeValueAsString(s); } catch (Exception e) { return "\"\""; }
    }
}
