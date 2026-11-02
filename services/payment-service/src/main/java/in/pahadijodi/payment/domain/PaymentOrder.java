package in.pahadijodi.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_order")
public class PaymentOrder {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "plan_code", nullable = false) private String planCode;
    @Column(name = "billing_period", nullable = false) private String billingPeriod;
    @Column(nullable = false) private String provider;
    @Column(nullable = false) private String currency;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(name = "tax_minor", nullable = false) private long taxMinor;
    @Column(name = "total_minor", nullable = false) private long totalMinor;
    @Column(name = "refunded_minor", nullable = false) private long refundedMinor;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrderStatus status;
    @Column(name = "provider_order_id", nullable = false) private String providerOrderId;
    @Column(name = "provider_payment_id") private String providerPaymentId;
    private String method;
    @Column(name = "failure_code") private String failureCode;
    @Column(name = "place_of_supply", nullable = false) private String placeOfSupply;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "client_params") private String clientParams;
    @Version private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "captured_at") private Instant capturedAt;

    protected PaymentOrder() {}

    public PaymentOrder(UUID id, UUID userId, String planCode, String billingPeriod, String provider, String currency,
                        long amountMinor, long taxMinor, String placeOfSupply, String idempotencyKey) {
        this.id = id; this.userId = userId; this.planCode = planCode; this.billingPeriod = billingPeriod;
        this.provider = provider; this.currency = currency; this.amountMinor = amountMinor; this.taxMinor = taxMinor;
        this.totalMinor = amountMinor + taxMinor; this.placeOfSupply = placeOfSupply; this.idempotencyKey = idempotencyKey;
        this.status = OrderStatus.CREATED; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }

    public void attachProviderOrder(String providerOrderId, String clientParams) {
        this.providerOrderId = providerOrderId; this.clientParams = clientParams;
    }

    /** @return false (and no change) if the transition is not allowed from the current state. */
    public boolean transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) return false;
        this.status = target;
        this.updatedAt = Instant.now();
        return true;
    }

    public void markCaptured(String providerPaymentId, String method) {
        this.providerPaymentId = providerPaymentId; this.method = method; this.failureCode = null;
        this.capturedAt = Instant.now();
    }
    public void markFailed(String failureCode) { this.failureCode = failureCode; }

    public long refundable() { return totalMinor - refundedMinor; }
    /** Applies a processed refund and moves to PARTIALLY_REFUNDED / REFUNDED. */
    public void applyRefund(long amount) {
        this.refundedMinor += amount;
        transitionTo(refundedMinor >= totalMinor ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getPlanCode() { return planCode; }
    public String getBillingPeriod() { return billingPeriod; }
    public String getProvider() { return provider; }
    public String getCurrency() { return currency; }
    public long getAmountMinor() { return amountMinor; }
    public long getTaxMinor() { return taxMinor; }
    public long getTotalMinor() { return totalMinor; }
    public long getRefundedMinor() { return refundedMinor; }
    public OrderStatus getStatus() { return status; }
    public String getProviderOrderId() { return providerOrderId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getMethod() { return method; }
    public String getFailureCode() { return failureCode; }
    public String getPlaceOfSupply() { return placeOfSupply; }
    public String getClientParams() { return clientParams; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCapturedAt() { return capturedAt; }
}
