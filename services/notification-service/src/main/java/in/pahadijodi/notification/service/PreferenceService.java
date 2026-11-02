package in.pahadijodi.notification.service;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.notification.config.NotificationProperties;
import in.pahadijodi.notification.domain.Channel;
import in.pahadijodi.notification.domain.NotificationPreference;
import in.pahadijodi.notification.domain.PreferenceRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {
    private static final Set<String> LOCALES = Set.of("en", "hi");

    /** Effective preferences (saved row, or defaults when the user never saved any). */
    public record Prefs(boolean inApp, boolean email, boolean sms, boolean push, boolean whatsapp,
                        Integer quietStart, Integer quietEnd, String locale) {
        public boolean enabled(Channel c) {
            return switch (c) {
                case IN_APP -> inApp;
                case EMAIL -> email;
                case SMS -> sms;
                case PUSH -> push;
                case WHATSAPP -> whatsapp;
            };
        }
    }

    private final PreferenceRepository repo;
    private final NotificationProperties props;
    private final Clock clock;

    public PreferenceService(PreferenceRepository repo, NotificationProperties props, Clock clock) {
        this.repo = repo;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Prefs get(UUID userId) {
        return repo.findById(userId).map(PreferenceService::from).orElseGet(this::defaults);
    }

    @Transactional
    public Prefs update(UUID userId, Prefs p) {
        if (p.locale() == null || !LOCALES.contains(p.locale())) throw invalid("locale must be en or hi");
        if ((p.quietStart() == null) != (p.quietEnd() == null)) throw invalid("quietStart and quietEnd must be set together");
        if (p.quietStart() != null && (p.quietStart() < 0 || p.quietStart() > 23 || p.quietEnd() < 0 || p.quietEnd() > 23)) {
            throw invalid("quiet hours must be 0-23 (IST)");
        }
        var row = repo.findById(userId).orElseGet(() -> new NotificationPreference(userId));
        row.apply(p.inApp(), p.email(), p.sms(), p.push(), p.whatsapp(), p.quietStart(), p.quietEnd(), p.locale(), clock.instant());
        return from(repo.save(row));
    }

    /** WhatsApp is opt-in (marketing-style, policy-sensitive); everything else defaults on. */
    private Prefs defaults() {
        var q = props.quietHours();
        return new Prefs(true, true, true, true, false, q.startHour(), q.endHour(), "en");
    }

    private static Prefs from(NotificationPreference n) {
        return new Prefs(n.isInApp(), n.isEmail(), n.isSms(), n.isPush(), n.isWhatsapp(), n.getQuietStart(), n.getQuietEnd(), n.getLocale());
    }

    private static PjException invalid(String m) { return new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", m); }
}
