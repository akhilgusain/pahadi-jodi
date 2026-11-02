package in.pahadijodi.airec.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AiUsageRepository extends JpaRepository<AiUsage, String> {
    @Transactional
    @Modifying
    @Query("update AiUsage u set u.calls = u.calls + 1 where u.id = :id")
    int increment(@Param("id") String id);
}
