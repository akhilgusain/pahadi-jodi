package in.pahadijodi.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.notification.config.NotificationProperties;
import in.pahadijodi.notification.domain.*;
import in.pahadijodi.notification.service.PreferenceService.Prefs;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a NotificationRequest into: an inbox row (IN_APP), one delivery_log row per external channel, each either
 * SUPPRESSED (opt-out / daily cap), PENDING now, or PENDING deferred to the end of the user's quiet hours.
 * The DeliveryWorker performs the actual sends (with retry + dead letter).
 */
@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final DeliveryLogRepository deliveries;
    private final PreferenceService preferences;
    private final TemplateCatalog templates;
    private final NotificationProperties props;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, DeliveryLogRepository deliveries,
                               PreferenceService preferences, TemplateCatalog templates, NotificationProperties props,
                               ApplicationEventPublisher events, ObjectMapper mapper, Clock clock) {
        this.notifications = notifications;
        this.deliveries = deliveries;
        this.preferences = preferences;
        this.templates = templates;
        this.props = props;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** @return the notification id, or empty when it was a duplicate (already processed). */
    @Transactional
    public Optional<UUID> process(NotificationRequest r) {
        if (notifications.existsByUserIdAndDedupeKey(r.userId(), r.dedupeKey())) return Optional.empty();
        Instant now = clock.instant();
        Prefs prefs = preferences.get(r.userId());
        var text = templates.render(r.type(), prefs.locale(), r.params());

        Map<String, String> data = new LinkedHashMap<>(r.data());
        data.putIfAbsent("deepLink", r.type().deepLink());
        data.put("type", r.type().name());

        boolean inbox = r.type().channels().contains(Channel.IN_APP) && prefs.inApp();
        Notification n = notifications.save(new Notification(r.userId(), r.type(), text.title(), text.body(), json(data),
                r.dedupeKey(), inbox, now));

        boolean engagement = r.type().category() == Category.ENGAGEMENT;
        boolean quiet = engagement && QuietHours.isQuiet(prefs.quietStart(), prefs.quietEnd(), now);
        for (Channel ch : r.type().channels()) {
            if (ch == Channel.IN_APP) continue;
            boolean mandatory = !engagement && ch == Channel.EMAIL;      // account/payment email cannot be opted out
            if (!prefs.enabled(ch) && !mandatory) {
                deliveries.save(DeliveryLog.suppressed(n.getId(), r.userId(), ch, "PREF_DISABLED", now));
            } else if (engagement && capReached(r.userId(), ch, now)) {
                deliveries.save(DeliveryLog.suppressed(n.getId(), r.userId(), ch, "DAILY_CAP", now));
            } else {
                Instant due = quiet ? QuietHours.endOfQuiet(prefs.quietEnd(), now) : now;
                deliveries.save(DeliveryLog.pending(n.getId(), r.userId(), ch, due, now));
            }
        }
        if (inbox) events.publishEvent(new NotificationCreated(r.userId(), NotificationDto.from(n, mapper)));
        return Optional.of(n.getId());
    }

    // ---- inbox -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<NotificationDto> inbox(UUID userId, int page, int size, boolean unreadOnly) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
        var result = unreadOnly
                ? notifications.findByUserIdAndInboxTrueAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notifications.findByUserIdAndInboxTrueOrderByCreatedAtDesc(userId, pageable);
        return result.map(n -> NotificationDto.from(n, mapper));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) { return notifications.countByUserIdAndInboxTrueAndReadAtIsNull(userId); }

    @Transactional
    public NotificationDto markRead(UUID userId, UUID id) {
        Notification n = notifications.findByIdAndUserId(id, userId).filter(Notification::isInbox)
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found"));
        n.markRead(clock.instant());
        return NotificationDto.from(n, mapper);
    }

    @Transactional
    public int markAllRead(UUID userId) { return notifications.markAllRead(userId, clock.instant()); }

    @Transactional(readOnly = true)
    public List<DeliveryLog> recentDeliveries(UUID userId) { return deliveries.findTop50ByUserIdOrderByCreatedAtDesc(userId); }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private boolean capReached(UUID user, Channel ch, Instant now) {
        int cap = switch (ch) {
            case PUSH -> props.dailyCaps().push();
            case EMAIL -> props.dailyCaps().email();
            case SMS -> props.dailyCaps().sms();
            case WHATSAPP -> props.dailyCaps().whatsapp();
            case IN_APP -> Integer.MAX_VALUE;
        };
        Instant dayStart = LocalDate.ofInstant(now, QuietHours.IST).atStartOfDay(QuietHours.IST).toInstant();
        long used = deliveries.countByUserIdAndChannelAndStatusInAndCreatedAtGreaterThanEqual(
                user, ch, EnumSet.of(DeliveryStatus.PENDING, DeliveryStatus.SENT), dayStart);
        return used >= cap;
    }

    private String json(Map<String, String> data) {
        try { return mapper.writeValueAsString(data); } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
