package in.pahadijodi.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Auto-renewal mandate record (UPI AutoPay / card-on-file token). MVP: record only, charge scheduling comes later. */
@Entity
@Table(name = "mandate")
public class Mandate {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private String provider;
    @Column(name = "provider_mandate_id", nullable = false) private String providerMandateId;
    @Column(name = "plan_code", nullable = false) private String planCode;
    @Column(name = "billing_period", nullable = false) private String billingPeriod;
    @Column(nullable = false) private String method;
    @Column(nullable = false) private String status;
    @Column(name = "max_amount_minor", nullable = false) private long maxAmountMinor;
    @Column(nullable = false) private String currency;
    @Column(name = "next_charge_at") private Instant nextChargeAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Mandate() {}
    public Mandate(UUID userId, String provider, String providerMandateId, String planCode, String billingPeriod,
                   String method, long maxAmountMinor, String currency, Instant nextChargeAt) {
        this.id = UUID.randomUUID(); this.userId = userId; this.provider = provider; this.providerMandateId = providerMandateId;
        this.planCode = planCode; this.billingPeriod = billingPeriod; this.method = method; this.status = "ACTIVE";
        this.maxAmountMinor = maxAmountMinor; this.currency = currency; this.nextChargeAt = nextChargeAt; this.createdAt = Instant.now();
    }
    public void cancel() { this.status = "CANCELLED"; this.nextChargeAt = null; }
    public boolean isActive() { return "ACTIVE".equals(status); }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getProvider() { return provider; }
    public String getPlanCode() { return planCode; }
    public String getBillingPeriod() { return billingPeriod; }
    public String getMethod() { return method; }
    public String getStatus() { return status; }
    public long getMaxAmountMinor() { return maxAmountMinor; }
    public String getCurrency() { return currency; }
    public Instant getNextChargeAt() { return nextChargeAt; }
}
