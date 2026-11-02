package in.pahadijodi.airec.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "llm_cache")
public class LlmCacheEntry {
    @Id @Column(name = "cache_key") private String cacheKey;
    @Column(name = "prompt_key", nullable = false) private String promptKey;
    @Column(nullable = false) private String response;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected LlmCacheEntry() {}
    public LlmCacheEntry(String cacheKey, String promptKey, String response) {
        this.cacheKey = cacheKey; this.promptKey = promptKey; this.response = response; this.createdAt = Instant.now();
    }
    public String getResponse() { return response; }
    public Instant getCreatedAt() { return createdAt; }
}
