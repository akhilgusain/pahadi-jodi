package in.pahadijodi.airec.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileProjectionRepository extends JpaRepository<ProfileProjection, UUID> {
    Optional<ProfileProjection> findByUserId(UUID userId);
    List<ProfileProjection> findByEmbeddingVersion(String embeddingVersion);
}
