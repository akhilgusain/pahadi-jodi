package in.pahadijodi.matchmaking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shortlist_entry")
public class ShortlistEntry {
    public enum Kind { FAVORITE, IGNORE, BLOCK, PRIVATE }

    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "target_profile_id", nullable = false) private UUID targetProfileId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Kind kind;
    @Column(name = "list_name", nullable = false) private String listName = "";
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ShortlistEntry() {}
    public ShortlistEntry(UUID userId, UUID targetProfileId, Kind kind, String listName) {
        this.id = UUID.randomUUID();
        this.userId = userId; this.targetProfileId = targetProfileId; this.kind = kind;
        this.listName = listName == null ? "" : listName;
        this.createdAt = Instant.now();
    }
    public UUID getUserId() { return userId; }
    public UUID getTargetProfileId() { return targetProfileId; }
    public Kind getKind() { return kind; }
    public String getListName() { return listName; }
    public Instant getCreatedAt() { return createdAt; }
}
