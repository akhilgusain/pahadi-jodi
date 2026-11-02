package in.pahadijodi.admin.audit;

import jakarta.persistence.*;

/** Single-row pointer to the chain tail. Locked FOR UPDATE on append, which also serialises writers. */
@Entity
@Table(name = "audit_head")
public class AuditHead {
    @Id private int id;
    @Column(name = "last_seq", nullable = false) private long lastSeq;
    @Column(name = "last_hash", nullable = false) private String lastHash;

    protected AuditHead() {}

    public void advance(long seq, String hash) { this.lastSeq = seq; this.lastHash = hash; }
    public long getLastSeq() { return lastSeq; }
    public String getLastHash() { return lastHash; }
}
