package in.pahadijodi.airec.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ai_usage")
public class AiUsage {
    @Id private String id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "usage_date", nullable = false) private LocalDate usageDate;
    @Column(nullable = false) private int calls;

    protected AiUsage() {}
    public AiUsage(UUID userId, LocalDate date) {
        this.id = userId + ":" + date; this.userId = userId; this.usageDate = date; this.calls = 1;
    }
    public int getCalls() { return calls; }
}
