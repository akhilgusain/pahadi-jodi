package in.pahadijodi.fraud.domain;

import in.pahadijodi.fraud.text.Simhash;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile_fingerprint")
public class ProfileFingerprint {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "text_hash", nullable = false) private String textHash;
    @Column(nullable = false) private long simhash;
    @Column(nullable = false) private int band0;
    @Column(nullable = false) private int band1;
    @Column(nullable = false) private int band2;
    @Column(nullable = false) private int band3;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ProfileFingerprint() {}
    public ProfileFingerprint(UUID userId) { this.userId = userId; }

    public void set(String textHash, long simhash) {
        this.textHash = textHash; this.simhash = simhash;
        this.band0 = Simhash.band(simhash, 0); this.band1 = Simhash.band(simhash, 1);
        this.band2 = Simhash.band(simhash, 2); this.band3 = Simhash.band(simhash, 3);
        this.updatedAt = Instant.now();
    }
    public UUID getUserId() { return userId; }
    public String getTextHash() { return textHash; }
    public long getSimhash() { return simhash; }
    public int getBand0() { return band0; }
    public int getBand1() { return band1; }
    public int getBand2() { return band2; }
    public int getBand3() { return band3; }
}
