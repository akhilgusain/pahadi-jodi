package in.pahadijodi.payment.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_item")
public class ReconciliationItem {
    @Id private UUID id;
    @Column(name = "run_id", nullable = false) private UUID runId;
    @Column(name = "provider_payment_id") private String providerPaymentId;
    @Column(nullable = false) private String kind;
    @Column(name = "local_amount") private Long localAmount;
    @Column(name = "provider_amount") private Long providerAmount;
    private String note;

    protected ReconciliationItem() {}
    public ReconciliationItem(UUID runId, String providerPaymentId, String kind, Long local, Long provider, String note) {
        this.id = UUID.randomUUID(); this.runId = runId; this.providerPaymentId = providerPaymentId; this.kind = kind;
        this.localAmount = local; this.providerAmount = provider; this.note = note;
    }
    public UUID getRunId() { return runId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getKind() { return kind; }
    public Long getLocalAmount() { return localAmount; }
    public Long getProviderAmount() { return providerAmount; }
    public String getNote() { return note; }
}
