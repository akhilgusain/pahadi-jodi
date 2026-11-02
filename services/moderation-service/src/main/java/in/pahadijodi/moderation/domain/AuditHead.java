package in.pahadijodi.moderation.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "audit_head")
public class AuditHead {
    @Id private Integer id;
    @Column(name = "last_hash", nullable = false) private String lastHash;
    protected AuditHead() {}
    public String getLastHash() { return lastHash; }
    public void setLastHash(String h) { this.lastHash = h; }
}
