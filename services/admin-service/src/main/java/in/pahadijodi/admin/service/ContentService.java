package in.pahadijodi.admin.service;

import in.pahadijodi.admin.audit.AuditService;
import in.pahadijodi.admin.domain.ContentItem;
import in.pahadijodi.admin.domain.ContentItem.Status;
import in.pahadijodi.admin.domain.ContentItem.Type;
import in.pahadijodi.admin.domain.Repositories.ContentRepository;
import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentService {
    public record Draft(Type type, String slug, String locale, String title, String body, String imageUrl, String linkUrl, int sortOrder) {}

    private final ContentRepository repo;
    private final AuditService audit;
    private final OutboxService outbox;

    public ContentService(ContentRepository repo, AuditService audit, OutboxService outbox) {
        this.repo = repo; this.audit = audit; this.outbox = outbox;
    }

    @Transactional
    public ContentItem create(Actor actor, Draft d) {
        String locale = d.locale() == null ? "en" : d.locale();
        if (repo.findByTypeAndSlugAndLocale(d.type(), d.slug(), locale).isPresent()) {
            throw new PjException(HttpStatus.CONFLICT, "CONTENT_SLUG_TAKEN", "Slug already used for this type and locale");
        }
        var c = new ContentItem(d.type(), d.slug(), locale, actor.id());
        c.edit(d.title(), d.body(), d.imageUrl(), d.linkUrl(), d.sortOrder(), actor.id());
        repo.save(c);
        audit.append(actor.id(), actor.roles(), "CONTENT_CREATED", "CONTENT", c.getId().toString(), Map.of("type", d.type().name(), "slug", d.slug()));
        return c;
    }

    @Transactional
    public ContentItem update(Actor actor, UUID id, Draft d) {
        ContentItem c = get(id);
        c.edit(d.title(), d.body(), d.imageUrl(), d.linkUrl(), d.sortOrder(), actor.id());
        audit.append(actor.id(), actor.roles(), "CONTENT_UPDATED", "CONTENT", id.toString(), Map.of("slug", c.getSlug()));
        return c;
    }

    @Transactional
    public ContentItem publish(Actor actor, UUID id) {
        ContentItem c = get(id);
        c.publish(actor.id());
        outbox.publish("pj.admin.content-published.v1", "ContentPublished", id.toString(),
                Map.of("contentId", id.toString(), "type", c.getType().name(), "slug", c.getSlug(), "locale", c.getLocale()));
        audit.append(actor.id(), actor.roles(), "CONTENT_PUBLISHED", "CONTENT", id.toString(), Map.of("slug", c.getSlug()));
        return c;
    }

    @Transactional
    public ContentItem archive(Actor actor, UUID id) {
        ContentItem c = get(id);
        c.archive(actor.id());
        audit.append(actor.id(), actor.roles(), "CONTENT_ARCHIVED", "CONTENT", id.toString(), Map.of("slug", c.getSlug()));
        return c;
    }

    @Transactional
    public void delete(Actor actor, UUID id) {
        ContentItem c = get(id);
        if (c.getStatus() == Status.PUBLISHED) throw new PjException(HttpStatus.CONFLICT, "CONTENT_PUBLISHED", "Archive published content before deleting it");
        repo.delete(c);
        audit.append(actor.id(), actor.roles(), "CONTENT_DELETED", "CONTENT", id.toString(), Map.of("slug", c.getSlug()));
    }

    @Transactional(readOnly = true)
    public List<ContentItem> list(Type type) {
        return type == null ? repo.findAllByOrderByUpdatedAtDesc() : repo.findByTypeOrderByUpdatedAtDesc(type);
    }

    // ---- public reads: published only -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ContentItem publicPage(String slug, String locale) {
        return repo.findByTypeAndSlugAndLocaleAndStatus(Type.PAGE, slug, locale, Status.PUBLISHED)
                .or(() -> repo.findByTypeAndSlugAndLocaleAndStatus(Type.PAGE, slug, "en", Status.PUBLISHED))   // fall back to English
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "PAGE_NOT_FOUND", "Page not found"));
    }

    @Transactional(readOnly = true)
    public List<ContentItem> publicList(Type type, String locale) {
        var items = repo.findByTypeAndLocaleAndStatusOrderBySortOrderAsc(type, locale, Status.PUBLISHED);
        return items.isEmpty() && !"en".equals(locale) ? repo.findByTypeAndLocaleAndStatusOrderBySortOrderAsc(type, "en", Status.PUBLISHED) : items;
    }

    private ContentItem get(UUID id) {
        return repo.findById(id).orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "Content not found"));
    }
}
