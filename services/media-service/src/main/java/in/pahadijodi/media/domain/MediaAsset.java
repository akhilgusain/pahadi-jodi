package in.pahadijodi.media.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_asset")
public class MediaAsset {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MediaKind kind;
    @Column(name = "content_type", nullable = false) private String contentType;
    @Column(name = "declared_size", nullable = false) private long declaredSize;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MediaStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Privacy privacy;
    @Column(name = "storage_key") private String storageKey;
    @Column(name = "thumb_key") private String thumbKey;
    private Integer width;
    private Integer height;
    @Column(nullable = false) private int position;
    @Column(name = "is_primary", nullable = false) private boolean primaryPhoto;
    @Column(name = "reject_reason") private String rejectReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected MediaAsset() {}

    public MediaAsset(UUID userId, MediaKind kind, String contentType, long declaredSize, Privacy privacy) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.kind = kind;
        this.contentType = contentType;
        this.declaredSize = declaredSize;
        this.privacy = privacy;
        this.status = MediaStatus.UPLOAD_PENDING;
        this.createdAt = this.updatedAt = Instant.now();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public void uploaded(String rawKey, long size) {
        this.storageKey = rawKey;
        this.sizeBytes = size;
        this.status = MediaStatus.UPLOADED;
        touch();
    }
    public void scanning() { this.status = MediaStatus.SCANNING; touch(); }

    public void ready(String key, String thumb, String contentType, long size, Integer w, Integer h) {
        this.storageKey = key;
        this.thumbKey = thumb;
        this.contentType = contentType;
        this.sizeBytes = size;
        this.width = w;
        this.height = h;
        this.status = MediaStatus.READY;
        touch();
    }

    public void reject(String reason) {
        this.status = MediaStatus.REJECTED;
        this.rejectReason = reason;
        this.storageKey = null;
        this.thumbKey = null;
        this.primaryPhoto = false;
        touch();
    }

    public void setPrivacy(Privacy p) { this.privacy = p; touch(); }
    public void setPosition(int p) { this.position = p; touch(); }
    public void setPrimary(boolean p) { this.primaryPhoto = p; touch(); }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public MediaKind getKind() { return kind; }
    public String getContentType() { return contentType; }
    public long getDeclaredSize() { return declaredSize; }
    public long getSizeBytes() { return sizeBytes; }
    public MediaStatus getStatus() { return status; }
    public Privacy getPrivacy() { return privacy; }
    public String getStorageKey() { return storageKey; }
    public String getThumbKey() { return thumbKey; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public int getPosition() { return position; }
    public boolean isPrimary() { return primaryPhoto; }
    public String getRejectReason() { return rejectReason; }
    public Instant getCreatedAt() { return createdAt; }
}
