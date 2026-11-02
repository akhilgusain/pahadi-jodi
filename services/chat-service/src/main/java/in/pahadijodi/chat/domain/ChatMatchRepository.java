package in.pahadijodi.chat.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMatchRepository extends JpaRepository<ChatMatch, UUID> {
    boolean existsByUserAAndUserB(UUID userA, UUID userB);
}
