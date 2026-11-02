package in.pahadijodi.notification.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {
    Optional<DeviceToken> findByToken(String token);
    List<DeviceToken> findByUserIdOrderByLastSeenAtDesc(UUID userId);

    @Transactional
    long deleteByUserIdAndToken(UUID userId, String token);

    @Transactional
    long deleteByTokenIn(Collection<String> tokens);
}
