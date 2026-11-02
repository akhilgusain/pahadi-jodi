package in.pahadijodi.media.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationCheckRepository extends JpaRepository<VerificationCheck, UUID> {
    List<VerificationCheck> findByUserId(UUID userId);
    Optional<VerificationCheck> findByUserIdAndType(UUID userId, VerificationCheck.Type type);
    boolean existsByTypeAndIdHashAndUserIdNot(VerificationCheck.Type type, String idHash, UUID userId);
    List<VerificationCheck> findByStatusOrderByUpdatedAtAsc(VerificationCheck.Status status);
}
