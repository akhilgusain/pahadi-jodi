package in.pahadijodi.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "preference")
public class Preference {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(nullable = false) private String language = "en";
    @Column(name = "notify_email", nullable = false) private boolean notifyEmail = true;
    @Column(name = "notify_sms", nullable = false) private boolean notifySms = true;
    @Column(name = "notify_push", nullable = false) private boolean notifyPush = true;
    @Column(name = "notify_whatsapp", nullable = false) private boolean notifyWhatsapp = false;
    @Column(name = "profile_visibility", nullable = false) private String profileVisibility = "ALL_MEMBERS";
    @Column(name = "allow_direct_messages", nullable = false) private String allowDirectMessages = "PREMIUM";
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    protected Preference() {}
    public Preference(UUID userId) { this.userId = userId; }

    public String getLanguage() { return language; }
    public boolean isNotifyEmail() { return notifyEmail; }
    public boolean isNotifySms() { return notifySms; }
    public boolean isNotifyPush() { return notifyPush; }
    public boolean isNotifyWhatsapp() { return notifyWhatsapp; }
    public String getProfileVisibility() { return profileVisibility; }
    public String getAllowDirectMessages() { return allowDirectMessages; }

    public void update(String language, boolean email, boolean sms, boolean push, boolean whatsapp,
                       String visibility, String directMessages) {
        this.language = language; this.notifyEmail = email; this.notifySms = sms; this.notifyPush = push;
        this.notifyWhatsapp = whatsapp; this.profileVisibility = visibility; this.allowDirectMessages = directMessages;
        this.updatedAt = Instant.now();
    }
}
