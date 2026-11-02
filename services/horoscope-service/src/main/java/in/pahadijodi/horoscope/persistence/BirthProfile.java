package in.pahadijodi.horoscope.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "birth_profile")
public class BirthProfile {
    public enum Visibility { HIDDEN, MATCHES_ONLY, ON_INTEREST }

    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "enc_payload", nullable = false, columnDefinition = "TEXT") private String encPayload;
    @Column(name = "birth_hash", nullable = false) private String birthHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Visibility visibility;
    @Column(name = "consent_at", nullable = false) private Instant consentAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected BirthProfile() {}
    public BirthProfile(UUID userId) {
        this.userId = userId;
        this.consentAt = Instant.now();
    }

    public void update(String encPayload, String birthHash, Visibility visibility) {
        this.encPayload = encPayload;
        this.birthHash = birthHash;
        this.visibility = visibility;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getEncPayload() { return encPayload; }
    public String getBirthHash() { return birthHash; }
    public Visibility getVisibility() { return visibility; }
    public boolean isShared() { return visibility != Visibility.HIDDEN; }
}
