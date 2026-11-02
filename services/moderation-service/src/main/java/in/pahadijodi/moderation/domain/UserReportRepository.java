package in.pahadijodi.moderation.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserReportRepository extends JpaRepository<UserReport, UUID> {
    boolean existsByReporterIdAndTargetUserIdAndCreatedAtAfter(UUID reporter, UUID target, Instant since);

    @Query("select count(distinct r.reporterId) from UserReport r where r.targetUserId = :target and r.createdAt > :since")
    long countDistinctReporters(@Param("target") UUID target, @Param("since") Instant since);
}
