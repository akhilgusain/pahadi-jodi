package in.pahadijodi.search.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_search")
public class SavedSearch {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private String name;
    @Column(name = "criteria_json", nullable = false, columnDefinition = "TEXT") private String criteriaJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected SavedSearch() {}
    public SavedSearch(UUID userId, String name, String criteriaJson) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.criteriaJson = criteriaJson;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
    public void update(String name, String criteriaJson) {
        this.name = name;
        this.criteriaJson = criteriaJson;
        this.updatedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getCriteriaJson() { return criteriaJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
