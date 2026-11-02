package in.pahadijodi.media.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID> {
    Optional<AccessGrant> findByOwnerIdAndViewerId(UUID ownerId, UUID viewerId);
    List<AccessGrant> findByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, AccessGrant.Status status);
}
