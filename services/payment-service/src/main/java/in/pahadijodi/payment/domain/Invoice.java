package in.pahadijodi.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoice")
public class Invoice {
    public enum Type { TAX_INVOICE, CREDIT_NOTE }

    @Id private UUID id;
    @Column(name = "invoice_no", nullable = false) private String invoiceNo;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Type type;
    @Column(name = "related_invoice_id") private UUID relatedInvoiceId;
    @Column(name = "place_of_supply", nullable = false) private String placeOfSupply;
    @Column(name = "hsn_sac", nullable = false) private String hsnSac = "998439";
    @Column(name = "taxable_minor", nullable = false) private long taxableMinor;
    @Column(name = "cgst_minor", nullable = false) private long cgstMinor;
    @Column(name = "sgst_minor", nullable = false) private long sgstMinor;
    @Column(name = "igst_minor", nullable = false) private long igstMinor;
    @Column(name = "total_minor", nullable = false) private long totalMinor;
    @Column(nullable = false) private String currency;
    @Column(name = "issued_at", nullable = false) private Instant issuedAt;

    protected Invoice() {}
    public Invoice(String invoiceNo, PaymentOrder o, Type type, UUID related, long taxable, long cgst, long sgst,
                   long igst, long total) {
        this.id = UUID.randomUUID(); this.invoiceNo = invoiceNo; this.orderId = o.getId(); this.userId = o.getUserId();
        this.type = type; this.relatedInvoiceId = related; this.placeOfSupply = o.getPlaceOfSupply();
        this.taxableMinor = taxable; this.cgstMinor = cgst; this.sgstMinor = sgst; this.igstMinor = igst;
        this.totalMinor = total; this.currency = o.getCurrency(); this.issuedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getInvoiceNo() { return invoiceNo; }
    public UUID getOrderId() { return orderId; }
    public Type getType() { return type; }
    public String getPlaceOfSupply() { return placeOfSupply; }
    public String getHsnSac() { return hsnSac; }
    public long getTaxableMinor() { return taxableMinor; }
    public long getCgstMinor() { return cgstMinor; }
    public long getSgstMinor() { return sgstMinor; }
    public long getIgstMinor() { return igstMinor; }
    public long getTotalMinor() { return totalMinor; }
    public String getCurrency() { return currency; }
    public Instant getIssuedAt() { return issuedAt; }
}
