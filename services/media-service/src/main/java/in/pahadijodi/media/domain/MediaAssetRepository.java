package in.pahadijodi.media.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    List<MediaAsset> findByUserIdOrderByKindAscPositionAscCreatedAtAsc(UUID userId);
    List<MediaAsset> findByUserIdAndKindAndStatusOrderByPositionAscCreatedAtAsc(UUID userId, MediaKind kind, MediaStatus status);
    Optional<MediaAsset> findFirstByUserIdAndKindAndPrimaryPhotoTrueAndStatus(UUID userId, MediaKind kind, MediaStatus status);
    long countByUserIdAndKindAndStatusIn(UUID userId, MediaKind kind, List<MediaStatus> statuses);
}
