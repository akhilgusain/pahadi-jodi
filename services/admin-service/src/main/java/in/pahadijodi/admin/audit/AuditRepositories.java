package in.pahadijodi.admin.audit;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public final class AuditRepositories {
    private AuditRepositories() {}

    /** Deliberately NOT a JpaRepository: no delete, no deleteAll, no update paths. */
    public interface AuditLogRepository extends Repository<AuditLog, Long> {
        <S extends AuditLog> S save(S entry);
        List<AuditLog> findBySeqGreaterThanOrderBySeqAsc(long seq, Pageable page);
        List<AuditLog> findAllByOrderBySeqDesc(Pageable page);
        long count();
    }

    public interface AuditHeadRepository extends JpaRepository<AuditHead, Integer> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select h from AuditHead h where h.id = 1")
        AuditHead lockHead();
    }
}
