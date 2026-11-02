package in.pahadijodi.matchmaking.service;

import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.matchmaking.domain.DailyRecommendation;
import in.pahadijodi.matchmaking.domain.Repositories.*;
import in.pahadijodi.matchmaking.scoring.Scoring.ScoredMatch;
import in.pahadijodi.matchmaking.service.Views.ProfileCard;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Daily top-N per user, idempotent per (user, date). Runs at 02:00 IST; also generated on demand for new users. */
@Service
public class DailyRecommendationService {
    private static final Logger log = LoggerFactory.getLogger(DailyRecommendationService.class);
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public record DailyItem(ProfileCard profile, double score, int rank, List<String> reasons) {}

    private final MatchService matchService;
    private final DailyRecommendationRepository daily;
    private final ProfileFacetRepository facets;
    private final OutboxService outbox;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final int size;

    public DailyRecommendationService(MatchService matchService, DailyRecommendationRepository daily, ProfileFacetRepository facets,
                                      OutboxService outbox, TransactionTemplate tx,
                                      @Value("${pj.matchmaking.daily.enabled:true}") boolean enabled,
                                      @Value("${pj.matchmaking.daily.size:20}") int size) {
        this.matchService = matchService; this.daily = daily; this.facets = facets;
        this.outbox = outbox; this.tx = tx; this.enabled = enabled; this.size = size;
    }

    @Scheduled(cron = "${pj.matchmaking.daily.cron:0 0 2 * * *}", zone = "Asia/Kolkata")
    public void nightly() {
        if (!enabled) return;
        runAll(LocalDate.now(IST));
    }

    /** Batch over all members. One transaction per user so a failure does not roll back everyone. */
    public int runAll(LocalDate date) {
        int ok = 0;
        for (var f : facets.findAll()) {
            try {
                generate(f.getUserId(), date);
                ok++;
            } catch (RuntimeException e) {
                log.warn("Daily recommendations failed for a user: {}", e.toString());
            }
        }
        return ok;
    }

    public List<DailyItem> generate(UUID userId, LocalDate date) {
        List<ScoredMatch> top = matchService.rank(userId, f -> true, true, size);
        tx.executeWithoutResult(s -> {
            daily.clear(userId, date);
            daily.flush();
            int rank = 1;
            for (ScoredMatch m : top) {
                daily.save(new DailyRecommendation(userId, date, m.profile().getProfileId(), rank++, m.score(), String.join("|", m.reasons())));
            }
            outbox.publish("pj.match.daily-recommendations-ready.v1", "DailyRecommendationsReady", userId.toString(),
                    Map.of("userId", userId.toString(), "date", date.toString(), "count", top.size()));
        });
        return read(userId, date);
    }

    public List<DailyItem> today(UUID userId) {
        LocalDate d = LocalDate.now(IST);
        List<DailyItem> items = read(userId, d);
        return items.isEmpty() ? generate(userId, d) : items;
    }

    private List<DailyItem> read(UUID userId, LocalDate date) {
        return daily.findByUserIdAndRecDateOrderByRankNo(userId, date).stream()
                .map(r -> facets.findById(r.getProfileId()).map(f -> new DailyItem(ProfileCard.of(f), r.getScore(), r.getRankNo(),
                        r.getReasons() == null || r.getReasons().isEmpty() ? List.<String>of() : List.of(r.getReasons().split("\\|")))).orElse(null))
                .filter(i -> i != null).toList();
    }
}
