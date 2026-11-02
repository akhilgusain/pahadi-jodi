package in.pahadijodi.notification.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    boolean existsByUserIdAndDedupeKey(UUID userId, String dedupeKey);

    Page<Notification> findByUserIdAndInboxTrueOrderByCreatedAtDesc(UUID userId, Pageable page);

    Page<Notification> findByUserIdAndInboxTrueAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable page);

    long countByUserIdAndInboxTrueAndReadAtIsNull(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.userId = :u and n.inbox = true and n.readAt is null")
    int markAllRead(@Param("u") UUID user, @Param("now") Instant now);
}
