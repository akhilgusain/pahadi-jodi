package in.pahadijodi.matchmaking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Private note about a profile; visible only to its author. */
@Entity
@Table(name = "profile_note")
public class ProfileNote {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "target_profile_id", nullable = false) private UUID targetProfileId;
    @Column(nullable = false) private String note;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ProfileNote() {}
    public ProfileNote(UUID userId, UUID targetProfileId, String note) {
        this.id = UUID.randomUUID();
        this.userId = userId; this.targetProfileId = targetProfileId; this.note = note;
        this.updatedAt = Instant.now();
    }
    public void setNote(String note) { this.note = note; this.updatedAt = Instant.now(); }
    public UUID getTargetProfileId() { return targetProfileId; }
    public String getNote() { return note; }
    public Instant getUpdatedAt() { return updatedAt; }
}
