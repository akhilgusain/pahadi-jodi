package in.pahadijodi.admin.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public final class Repositories {
    private Repositories() {}

    public interface RestrictionRepository extends JpaRepository<UserRestriction, UUID> {}

    public interface ApprovalRepository extends JpaRepository<ApprovalRequest, UUID> {
        List<ApprovalRequest> findByStatusOrderByCreatedAtAsc(ApprovalRequest.Status status);
        boolean existsByTargetUserIdAndKindAndStatus(UUID user, ApprovalRequest.Kind kind, ApprovalRequest.Status status);
        long countByStatus(ApprovalRequest.Status status);
    }

    public interface ContentRepository extends JpaRepository<ContentItem, UUID> {
        Optional<ContentItem> findByTypeAndSlugAndLocale(ContentItem.Type type, String slug, String locale);
        Optional<ContentItem> findByTypeAndSlugAndLocaleAndStatus(ContentItem.Type type, String slug, String locale, ContentItem.Status status);
        List<ContentItem> findByTypeAndLocaleAndStatusOrderBySortOrderAsc(ContentItem.Type type, String locale, ContentItem.Status status);
        List<ContentItem> findByTypeOrderByUpdatedAtDesc(ContentItem.Type type);
        List<ContentItem> findAllByOrderByUpdatedAtDesc();
    }
}
