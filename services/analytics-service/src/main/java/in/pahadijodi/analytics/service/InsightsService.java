package in.pahadijodi.analytics.service;

import static in.pahadijodi.analytics.model.EventNames.*;

import in.pahadijodi.analytics.mapping.Pseudonymizer;
import in.pahadijodi.analytics.store.AnalyticsStore;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Premium Plus member insights: only ever the caller's own numbers, looked up by their pseudonym. */
@Service
public class InsightsService {
    private final AnalyticsStore store;
    private final Pseudonymizer pseudo;
    private final Clock clock;

    public InsightsService(AnalyticsStore store, Pseudonymizer pseudo, Clock clock) {
        this.store = store;
        this.pseudo = pseudo;
        this.clock = clock;
    }

    public Map<String, Object> insights(UUID userId, int windowDays) {
        if (windowDays < 1 || windowDays > 90) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RANGE", "days must be 1..90");
        String me = pseudo.hash(userId.toString());
        LocalDate to = LocalDate.now(clock), from = to.minusDays(windowDays - 1L);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("windowDays", windowDays);
        out.put("from", from.toString());
        out.put("to", to.toString());
        out.put("profileViews", series(store.dailyCountForTarget(PROFILE_VIEWED, me, from, to), from, to,
                store.uniqueActorsForTarget(PROFILE_VIEWED, me, from, to)));
        out.put("interestsReceived", series(store.dailyCountForTarget(INTEREST_SENT, me, from, to), from, to,
                store.uniqueActorsForTarget(INTEREST_SENT, me, from, to)));
        out.put("interestsSent", series(store.dailyCountForActor(INTEREST_SENT, me, from, to), from, to, null));
        out.put("matches", series(store.dailyCountForActor(MATCH_CREATED, me, from, to), from, to, null));
        return out;
    }

    /** Zero-filled daily series so charts have no gaps. */
    private static Map<String, Object> series(Map<LocalDate, Long> counts, LocalDate from, LocalDate to, Long unique) {
        List<Map<String, Object>> daily = new ArrayList<>();
        long total = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            long c = counts.getOrDefault(d, 0L);
            total += c;
            daily.add(Map.of("day", d.toString(), "count", c));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        if (unique != null) m.put("unique", unique);
        m.put("daily", daily);
        return m;
    }
}
