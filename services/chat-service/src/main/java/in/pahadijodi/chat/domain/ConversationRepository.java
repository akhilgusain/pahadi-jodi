package in.pahadijodi.chat.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findByUserAAndUserB(UUID userA, UUID userB);

    @Query("select c from Conversation c where c.userA = :u or c.userB = :u order by coalesce(c.lastMessageAt, c.createdAt) desc")
    List<Conversation> findForUser(@Param("u") UUID user);
}
