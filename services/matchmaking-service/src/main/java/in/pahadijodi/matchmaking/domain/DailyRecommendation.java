package in.pahadijodi.matchmaking.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_recommendation")
public class DailyRecommendation {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "rec_date", nullable = false) private LocalDate recDate;
    @Column(name = "profile_id", nullable = false) private UUID profileId;
    @Column(name = "rank_no", nullable = false) private int rankNo;
    @Column(nullable = false) private double score;
    private String reasons;

    protected DailyRecommendation() {}
    public DailyRecommendation(UUID userId, LocalDate date, UUID profileId, int rank, double score, String reasons) {
        this.id = UUID.randomUUID();
        this.userId = userId; this.recDate = date; this.profileId = profileId; this.rankNo = rank;
        this.score = score; this.reasons = reasons;
    }
    public UUID getUserId() { return userId; }
    public LocalDate getRecDate() { return recDate; }
    public UUID getProfileId() { return profileId; }
    public int getRankNo() { return rankNo; }
    public double getScore() { return score; }
    public String getReasons() { return reasons; }
}
