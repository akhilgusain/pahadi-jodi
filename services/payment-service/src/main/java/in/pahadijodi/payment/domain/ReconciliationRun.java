package in.pahadijodi.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_run")
public class ReconciliationRun {
    @Id private UUID id;
    @Column(nullable = false) private String provider;
    @Column(name = "business_date", nullable = false) private LocalDate businessDate;
    @Column(nullable = false) private int matched;
    @Column(nullable = false) private int mismatched;
    @Column(name = "missing_local", nullable = false) private int missingLocal;
    @Column(name = "missing_provider", nullable = false) private int missingProvider;
    @Column(name = "started_at", nullable = false) private Instant startedAt;

    protected ReconciliationRun() {}
    public ReconciliationRun(String provider, LocalDate date, int matched, int mismatched, int missingLocal, int missingProvider) {
        this.id = UUID.randomUUID(); this.provider = provider; this.businessDate = date; this.matched = matched;
        this.mismatched = mismatched; this.missingLocal = missingLocal; this.missingProvider = missingProvider;
        this.startedAt = Instant.now();
    }
    public void record(int matched, int mismatched, int missingLocal, int missingProvider) {
        this.matched = matched; this.mismatched = mismatched; this.missingLocal = missingLocal; this.missingProvider = missingProvider;
    }
    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public LocalDate getBusinessDate() { return businessDate; }
    public int getMatched() { return matched; }
    public int getMismatched() { return mismatched; }
    public int getMissingLocal() { return missingLocal; }
    public int getMissingProvider() { return missingProvider; }
}
