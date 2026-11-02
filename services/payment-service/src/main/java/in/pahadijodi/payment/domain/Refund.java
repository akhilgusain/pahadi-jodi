package in.pahadijodi.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund")
public class Refund {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "provider_refund_id") private String providerRefundId;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false) private String reason;
    @Column(nullable = false) private String status;
    @Column(name = "requested_by") private String requestedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Refund() {}
    public Refund(UUID orderId, long amountMinor, String reason, String requestedBy) {
        this.id = UUID.randomUUID(); this.orderId = orderId; this.amountMinor = amountMinor;
        this.reason = reason; this.requestedBy = requestedBy; this.status = "REQUESTED"; this.createdAt = Instant.now();
    }
    public void processed(String providerRefundId) { this.providerRefundId = providerRefundId; this.status = "PROCESSED"; }
    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getProviderRefundId() { return providerRefundId; }
    public long getAmountMinor() { return amountMinor; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
}
