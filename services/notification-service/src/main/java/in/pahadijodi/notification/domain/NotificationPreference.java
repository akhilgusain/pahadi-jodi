package in.pahadijodi.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preference")
public class NotificationPreference {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "in_app_enabled", nullable = false) private boolean inApp;
    @Column(name = "email_enabled", nullable = false) private boolean email;
    @Column(name = "sms_enabled", nullable = false) private boolean sms;
    @Column(name = "push_enabled", nullable = false) private boolean push;
    @Column(name = "whatsapp_enabled", nullable = false) private boolean whatsapp;
    @Column(name = "quiet_start") private Integer quietStart;
    @Column(name = "quiet_end") private Integer quietEnd;
    @Column(nullable = false) private String locale;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected NotificationPreference() {}
    public NotificationPreference(UUID userId) { this.userId = userId; }

    public void apply(boolean inApp, boolean email, boolean sms, boolean push, boolean whatsapp,
                      Integer quietStart, Integer quietEnd, String locale, Instant now) {
        this.inApp = inApp; this.email = email; this.sms = sms; this.push = push; this.whatsapp = whatsapp;
        this.quietStart = quietStart; this.quietEnd = quietEnd; this.locale = locale; this.updatedAt = now;
    }

    public boolean isInApp() { return inApp; }
    public boolean isEmail() { return email; }
    public boolean isSms() { return sms; }
    public boolean isPush() { return push; }
    public boolean isWhatsapp() { return whatsapp; }
    public Integer getQuietStart() { return quietStart; }
    public Integer getQuietEnd() { return quietEnd; }
    public String getLocale() { return locale; }
}
