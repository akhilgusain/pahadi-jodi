package in.pahadijodi.common.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String topic;
    @Column(name = "partition_key", nullable = false)
    private String partitionKey;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String envelope;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(UUID id, String topic, String partitionKey, String envelope) {
        this.id = id;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.envelope = envelope;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTopic() { return topic; }
    public String getPartitionKey() { return partitionKey; }
    public String getEnvelope() { return envelope; }
    public Instant getPublishedAt() { return publishedAt; }
    public void markPublished() { this.publishedAt = Instant.now(); }
}
