package in.pahadijodi.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fraud_rule")
public class FraudRule {
    @Id private String code;
    @Column(nullable = false) private String description;
    @Column(nullable = false) private int weight;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected FraudRule() {}

    public void update(boolean enabled, int weight, String by) {
        this.enabled = enabled; this.weight = weight; this.updatedBy = by; this.updatedAt = Instant.now();
    }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public int getWeight() { return weight; }
    public boolean isEnabled() { return enabled; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
