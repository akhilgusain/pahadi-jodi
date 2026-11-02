package in.pahadijodi.payment.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "invoice_sequence")
public class InvoiceSequence {
    @Id private String fy;
    @Column(name = "last_no", nullable = false) private long lastNo;

    protected InvoiceSequence() {}
    public InvoiceSequence(String fy) { this.fy = fy; this.lastNo = 0; }
    public long next() { return ++lastNo; }
}
