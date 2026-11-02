package in.pahadijodi.notification.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterRepository extends JpaRepository<DeadLetter, UUID> {
    List<DeadLetter> findByUserId(UUID userId);
}
