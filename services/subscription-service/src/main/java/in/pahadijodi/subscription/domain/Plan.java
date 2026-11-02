package in.pahadijodi.subscription.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "plan")
public class Plan {
    @Id private String code;
    @Column(nullable = false) private String name;
    @Column(name = "tier_rank", nullable = false) private int tierRank;
    @Column(name = "price_inr_minor", nullable = false) private long priceInrMinor;
    @Column(name = "price_usd_minor", nullable = false) private long priceUsdMinor;
    @Column(name = "duration_days", nullable = false) private int durationDays;
    @Column(nullable = false) private boolean active;

    protected Plan() {}
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getTierRank() { return tierRank; }
    public long getPriceInrMinor() { return priceInrMinor; }
    public long getPriceUsdMinor() { return priceUsdMinor; }
    public int getDurationDays() { return durationDays; }
    public boolean isActive() { return active; }
}
