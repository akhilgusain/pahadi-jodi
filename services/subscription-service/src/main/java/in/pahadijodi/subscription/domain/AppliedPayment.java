package in.pahadijodi.subscription.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applied_payment")
public class AppliedPayment {
    @Id @Column(name = "payment_id") private String paymentId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "applied_at", nullable = false) private Instant appliedAt = Instant.now();

    protected AppliedPayment() {}
    public AppliedPayment(String paymentId, UUID userId) { this.paymentId = paymentId; this.userId = userId; }
}
