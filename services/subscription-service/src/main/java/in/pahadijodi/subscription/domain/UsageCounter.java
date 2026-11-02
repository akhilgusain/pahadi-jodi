package in.pahadijodi.subscription.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "usage_counter")
public class UsageCounter {
    @Id private String id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "ent_key", nullable = false) private String key;
    @Column(name = "period_key", nullable = false) private String periodKey;
    @Column(nullable = false) private long used;

    protected UsageCounter() {}
    public UsageCounter(String id, UUID userId, String key, String periodKey) {
        this.id = id; this.userId = userId; this.key = key; this.periodKey = periodKey;
    }
    public String getId() { return id; }
    public long getUsed() { return used; }

    /** Increments only if the new total stays within the limit. Call on a row locked FOR UPDATE. */
    public boolean tryConsume(long n, long limit) {
        if (used + n > limit) return false;
        used += n;
        return true;
    }
}
