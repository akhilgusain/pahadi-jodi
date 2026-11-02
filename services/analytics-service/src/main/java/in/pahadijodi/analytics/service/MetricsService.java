package in.pahadijodi.analytics.service;

import static in.pahadijodi.analytics.model.EventNames.*;

import in.pahadijodi.analytics.store.AnalyticsStore;
import in.pahadijodi.analytics.store.AnalyticsStore.RevenueRow;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
public class MetricsService {
    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);
    static final int MAX_RANGE_DAYS = 366;

    private final AnalyticsStore store;
    private final Clock clock;

    public MetricsService(AnalyticsStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public LocalDate today() { return LocalDate.now(clock); }

    // ------------------------------------------------------------------ rollups

    /** Recompute today and the two previous business days (late events and replays land in recent days only). */
    @Scheduled(cron = "${pj.analytics.rollup-cron:0 5 * * * *}", zone = "${pj.analytics.timezone:Asia/Kolkata}")
    public void rollupRecent() {
        LocalDate t = today();
        for (int i = 0; i < 3; i++) rollup(t.minusDays(i));
    }

    public Map<String, Long> rollup(LocalDate day) {
        Map<String, Long> m = store.computeDayMetrics(day);
        store.replaceStoredMetrics(day, m);
        log.debug("rolled up {} -> {}", day, m);
        return m;
    }

    // ------------------------------------------------------------------ summary

    public Map<String, Object> summary(LocalDate from, LocalDate to) {
        range(from, to);
        LocalDate today = today();
        Map<LocalDate, Map<String, Long>> stored = store.storedMetrics(from, to);
        Map<String, Long> totals = new TreeMap<>();
        List<Map<String, Object>> daily = new ArrayList<>();
        long activeSum = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            // closed days come from the rollup when present; today (and unrolled days) are computed live
            Map<String, Long> m = (d.isBefore(today) && stored.containsKey(d)) ? stored.get(d) : store.computeDayMetrics(d);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", d.toString());
            row.put("metrics", m);
            daily.add(row);
            for (var e : m.entrySet()) {
                if (!e.getKey().equals(ACTIVE_USERS)) totals.merge(e.getKey(), e.getValue(), Long::sum);
            }
            activeSum += m.getOrDefault(ACTIVE_USERS, 0L);
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", from.toString());
        out.put("to", to.toString());
        out.put("totals", totals);
        out.put("uniqueActiveUsers", store.uniqueActiveUsers(from, to));
        out.put("avgDailyActiveUsers", Math.round(activeSum * 10.0 / days) / 10.0);
        out.put("daily", daily);
        return out;
    }

    // ------------------------------------------------------------------ funnel

    /**
     * Cohort funnel: members who registered in [from, to], then how many of them went on to each next stage.
     * Strict: a member counts at a stage only if they also reached every earlier stage. {@code reached} ignores order.
     */
    public Map<String, Object> funnel(LocalDate from, LocalDate to) {
        range(from, to);
        Map<String, Set<String>> byStage = store.cohortUsersByEvent(from, to, FUNNEL);
        // PROFILE_COMPLETE rows are stored under their own name; everything else under the canonical name
        Set<String> chain = null;
        List<Map<String, Object>> stages = new ArrayList<>();
        long start = 0, prev = 0;
        for (String stage : FUNNEL) {
            Set<String> reached = byStage.getOrDefault(stage, Set.of());
            chain = chain == null ? new HashSet<>(reached) : intersect(chain, reached);
            long users = chain.size();
            if (stage.equals(REGISTERED)) start = users;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stage", stage);
            row.put("users", users);
            row.put("reachedIgnoringOrder", reached.size());
            row.put("conversionFromPrevious", stage.equals(REGISTERED) ? null : ratio(users, prev));
            row.put("conversionFromStart", ratio(users, start));
            stages.add(row);
            prev = users;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cohortFrom", from.toString());
        out.put("cohortTo", to.toString());
        out.put("stages", stages);
        return out;
    }

    // ------------------------------------------------------------------ revenue

    public Map<String, Object> revenue(LocalDate from, LocalDate to) {
        range(from, to);
        List<RevenueRow> rows = store.revenue(from, to);
        Map<String, Long> byCurrency = new TreeMap<>();
        Map<String, Map<String, Long>> byPlan = new TreeMap<>();
        Map<String, Map<String, Long>> byDay = new TreeMap<>();
        long payments = 0, payerRows = 0;
        for (RevenueRow r : rows) {
            byCurrency.merge(r.currency(), r.amountMinor(), Long::sum);
            byPlan.computeIfAbsent(r.currency(), k -> new TreeMap<>()).merge(r.plan(), r.amountMinor(), Long::sum);
            byDay.computeIfAbsent(r.day().toString(), k -> new TreeMap<>()).merge(r.currency(), r.amountMinor(), Long::sum);
            payments += r.payments();
            payerRows += r.payers();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", from.toString());
        out.put("to", to.toString());
        out.put("amountsAreMinorUnits", true);
        out.put("totalByCurrency", byCurrency);
        out.put("byPlan", byPlan);
        out.put("byDay", byDay);
        out.put("paymentsCaptured", payments);
        // payers is summed per (day, plan) bucket: an upper bound on unique payers when someone pays on several days
        out.put("payerBucketsUpperBound", payerRows);
        return out;
    }

    // ------------------------------------------------------------------ retention

    /** Weekly registration cohorts (weeks start Monday): % of each cohort active in week 0, 1, 2 ... after registering. */
    public Map<String, Object> retention(int cohorts) {
        if (cohorts < 1 || cohorts > 26) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RANGE", "cohorts must be 1..26");
        LocalDate thisWeek = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate first = thisWeek.minusWeeks(cohorts - 1L);
        Map<String, LocalDate> regs = store.registrations(first);
        Map<String, Set<LocalDate>> activity = store.activityDaysOfCohort(first);

        Map<LocalDate, List<String>> byCohort = new TreeMap<>();
        for (var e : regs.entrySet()) {
            byCohort.computeIfAbsent(e.getValue().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), k -> new ArrayList<>()).add(e.getKey());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (var c : byCohort.entrySet()) {
            LocalDate start = c.getKey();
            int maxWeek = (int) ChronoUnit.WEEKS.between(start, thisWeek);
            List<Double> pct = new ArrayList<>();
            for (int w = 0; w <= maxWeek; w++) {
                long active = 0;
                for (String u : c.getValue()) {
                    for (LocalDate d : activity.getOrDefault(u, Set.of())) {
                        if ((int) ChronoUnit.WEEKS.between(start, d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) == w) { active++; break; }
                    }
                }
                pct.add(ratio(active, c.getValue().size()) * 100.0);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("cohortWeekStart", start.toString());
            row.put("size", c.getValue().size());
            row.put("retentionPercentByWeek", pct);
            out.add(row);
        }
        return Map.of("cohorts", out, "definition", "active = any event other than REGISTERED in that week");
    }

    // ------------------------------------------------------------------ helpers

    void range(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RANGE", "from must not be after to");
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RANGE", "range is limited to " + MAX_RANGE_DAYS + " days");
        }
    }

    private static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> r = new HashSet<>(a);
        r.retainAll(b);
        return r;
    }

    private static double ratio(long n, long d) {
        return d == 0 ? 0.0 : Math.round(n * 10000.0 / d) / 10000.0;
    }
}
