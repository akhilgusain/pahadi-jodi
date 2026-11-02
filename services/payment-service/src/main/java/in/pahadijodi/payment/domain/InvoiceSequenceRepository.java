package in.pahadijodi.payment.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InvoiceSequence> findWithLockByFy(String fy);
}
