package in.pahadijodi.fraud.domain;

import in.pahadijodi.fraud.text.Simhash;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "photo_hash")
public class PhotoHash {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private long phash;
    @Column(nullable = false) private int band0;
    @Column(nullable = false) private int band1;
    @Column(nullable = false) private int band2;
    @Column(nullable = false) private int band3;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PhotoHash() {}
    public PhotoHash(UUID userId, long phash) {
        this.id = UUID.randomUUID(); this.userId = userId; this.phash = phash;
        this.band0 = Simhash.band(phash, 0); this.band1 = Simhash.band(phash, 1);
        this.band2 = Simhash.band(phash, 2); this.band3 = Simhash.band(phash, 3);
        this.createdAt = Instant.now();
    }
    public UUID getUserId() { return userId; }
    public long getPhash() { return phash; }
}
