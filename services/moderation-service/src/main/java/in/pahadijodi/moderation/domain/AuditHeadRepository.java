package in.pahadijodi.moderation.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AuditHeadRepository extends JpaRepository<AuditHead, Integer> {
    /** SELECT ... FOR UPDATE on the single head row: serialises appends across instances. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuditHead h where h.id = 1")
    AuditHead lockHead();
}
