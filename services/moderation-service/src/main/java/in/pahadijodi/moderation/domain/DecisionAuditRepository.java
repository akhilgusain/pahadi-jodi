package in.pahadijodi.moderation.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Deliberately NOT a JpaRepository: no update/delete methods exist. */
public interface DecisionAuditRepository extends Repository<DecisionAudit, Long> {
    DecisionAudit save(DecisionAudit a);
    List<DecisionAudit> findAllByOrderBySeqAsc();
    List<DecisionAudit> findByCaseIdOrderBySeqAsc(UUID caseId);
}
