package in.pahadijodi.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_item")
public class ContentItem {
    public enum Type { PAGE, BANNER, FAQ, STORY }
    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(name = "content_type", nullable = false) private Type type;
    @Column(nullable = false) private String slug;
    @Column(nullable = false) private String locale;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String body;
    @Column(name = "image_url") private String imageUrl;
    @Column(name = "link_url") private String linkUrl;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "published_at") private Instant publishedAt;

    protected ContentItem() {}

    public ContentItem(Type type, String slug, String locale, String by) {
        this.id = UUID.randomUUID(); this.type = type; this.slug = slug; this.locale = locale; this.status = Status.DRAFT;
        this.createdBy = by; this.updatedBy = by; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }

    public void edit(String title, String body, String imageUrl, String linkUrl, int sortOrder, String by) {
        this.title = title; this.body = body; this.imageUrl = imageUrl; this.linkUrl = linkUrl; this.sortOrder = sortOrder;
        this.updatedBy = by; this.updatedAt = Instant.now();
    }
    public void publish(String by) { this.status = Status.PUBLISHED; this.publishedAt = Instant.now(); this.updatedBy = by; this.updatedAt = this.publishedAt; }
    public void archive(String by) { this.status = Status.ARCHIVED; this.updatedBy = by; this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public Type getType() { return type; }
    public String getSlug() { return slug; }
    public String getLocale() { return locale; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getImageUrl() { return imageUrl; }
    public String getLinkUrl() { return linkUrl; }
    public int getSortOrder() { return sortOrder; }
    public Status getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
