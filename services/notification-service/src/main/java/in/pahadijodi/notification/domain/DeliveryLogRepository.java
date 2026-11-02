package in.pahadijodi.notification.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, UUID> {
    @Query("select d.id from DeliveryLog d where d.status = :pending and d.nextAttemptAt <= :now order by d.nextAttemptAt")
    List<UUID> findDueIds(@Param("pending") DeliveryStatus pending, @Param("now") Instant now, Pageable page);

    long countByUserIdAndChannelAndStatusInAndCreatedAtGreaterThanEqual(UUID userId, Channel channel,
                                                                        Collection<DeliveryStatus> statuses, Instant since);

    List<DeliveryLog> findByNotificationIdOrderByCreatedAt(UUID notificationId);

    List<DeliveryLog> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);
}
