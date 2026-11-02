package in.pahadijodi.moderation.domain;

import in.pahadijodi.moderation.domain.Enums.AppealStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppealRepository extends JpaRepository<Appeal, UUID> {
    List<Appeal> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Appeal> findByStatusOrderByCreatedAtAsc(AppealStatus status);
    boolean existsByUserIdAndCaseIdAndStatus(UUID userId, UUID caseId, AppealStatus status);
}
