package in.pahadijodi.moderation.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserStrikeRepository extends JpaRepository<UserStrike, UUID> {
    @Query("select coalesce(sum(s.points), 0) from UserStrike s where s.userId = :user and s.revoked = false and s.createdAt > :since")
    long activePoints(@Param("user") UUID user, @Param("since") Instant since);

    List<UserStrike> findByCaseIdAndRevokedFalse(UUID caseId);
}
