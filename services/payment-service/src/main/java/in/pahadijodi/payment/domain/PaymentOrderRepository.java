package in.pahadijodi.payment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {
    Optional<PaymentOrder> findByUserIdAndIdempotencyKey(UUID userId, String key);
    Optional<PaymentOrder> findByProviderOrderId(String providerOrderId);
    Optional<PaymentOrder> findByProviderPaymentId(String providerPaymentId);
    List<PaymentOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<PaymentOrder> findByProviderAndCapturedAtBetween(String provider, Instant from, Instant to);
}
