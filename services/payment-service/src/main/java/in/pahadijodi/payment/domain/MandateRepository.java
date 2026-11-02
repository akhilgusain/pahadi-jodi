package in.pahadijodi.payment.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MandateRepository extends JpaRepository<Mandate, UUID> {
    List<Mandate> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Mandate> findByUserIdAndStatus(UUID userId, String status);
}
