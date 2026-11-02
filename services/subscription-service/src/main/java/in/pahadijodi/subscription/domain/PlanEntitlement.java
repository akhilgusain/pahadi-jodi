package in.pahadijodi.subscription.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/** Entitlements are data, not code: product changes quotas with a migration/admin update, no deploy. */
@Entity
@Table(name = "plan_entitlement")
@IdClass(PlanEntitlement.Key.class)
public class PlanEntitlement {
    public enum Kind { QUOTA, CAP, FLAG }
    public enum Period { DAY, MONTH, NONE }

    public static class Key implements Serializable {
        public String tier;
        public String key;
        public Key() {}
        @Override public boolean equals(Object o) { return o instanceof Key k && Objects.equals(tier, k.tier) && Objects.equals(key, k.key); }
        @Override public int hashCode() { return Objects.hash(tier, key); }
    }

    @Id private String tier;
    @Id @Column(name = "ent_key") private String key;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Kind kind;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "quota_limit") private Long quotaLimit;
    @Enumerated(EnumType.STRING) @Column(name = "period_unit", nullable = false) private Period periodUnit;
    private String variant;

    protected PlanEntitlement() {}
    public String getTier() { return tier; }
    public String getKey() { return key; }
    public Kind getKind() { return kind; }
    public boolean isEnabled() { return enabled; }
    public Long getQuotaLimit() { return quotaLimit; }
    public Period getPeriodUnit() { return periodUnit; }
    public String getVariant() { return variant; }
}
