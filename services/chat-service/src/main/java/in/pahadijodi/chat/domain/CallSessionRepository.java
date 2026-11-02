package in.pahadijodi.chat.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallSessionRepository extends JpaRepository<CallSession, UUID> {
    Optional<CallSession> findFirstByConversationIdAndStateIn(UUID conversationId, Collection<CallState> states);
    List<CallSession> findByStateAndCreatedAtBefore(CallState state, Instant before);
}
