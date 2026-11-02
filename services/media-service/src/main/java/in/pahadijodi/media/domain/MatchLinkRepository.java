package in.pahadijodi.media.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchLinkRepository extends JpaRepository<MatchLink, UUID> {
    boolean existsByUserAAndUserB(UUID a, UUID b);
}
