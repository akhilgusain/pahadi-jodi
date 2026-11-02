package in.pahadijodi.moderation.domain;

import in.pahadijodi.moderation.domain.Enums.CaseStatus;
import in.pahadijodi.moderation.domain.Enums.ContentType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationCaseRepository extends JpaRepository<ModerationCase, UUID> {
    List<ModerationCase> findByStatusInOrderByPriorityAscDueAtAsc(Collection<CaseStatus> statuses, Pageable page);
    List<ModerationCase> findBySubjectUserIdAndContentTypeAndStatusIn(UUID subject, ContentType type, Collection<CaseStatus> statuses);
}
