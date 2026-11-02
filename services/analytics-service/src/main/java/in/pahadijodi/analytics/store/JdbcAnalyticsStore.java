package in.pahadijodi.analytics.store;

import static in.pahadijodi.analytics.model.EventNames.*;

import in.pahadijodi.analytics.model.FactRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Plain, portable SQL (PostgreSQL and H2). Column names avoid reserved words (event_day, metric_value). */
@Repository
public class JdbcAnalyticsStore implements AnalyticsStore {
    private final JdbcClient jdbc;

    public JdbcAnalyticsStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    private static OffsetDateTime utc(Instant i) { return OffsetDateTime.ofInstant(i, ZoneOffset.UTC); }

    @Override
    public boolean append(FactRow r) {
        try {
            jdbc.sql("""
                    insert into analytics_event (id, event_id, event_name, topic, user_hash, target_hash, occurred_at, event_day,
                                                 amount_minor, currency, plan, community, district, received_at)
                    values (:id, :eventId, :name, :topic, :user, :target, :at, :day, :amount, :cur, :plan, :comm, :dist, :now)""")
                    .param("id", UUID.randomUUID()).param("eventId", r.eventId()).param("name", r.eventName()).param("topic", r.topic())
                    .param("user", r.userHash()).param("target", r.targetHash()).param("at", utc(r.occurredAt())).param("day", r.day())
                    .param("amount", r.amountMinor()).param("cur", r.currency()).param("plan", r.plan())
                    .param("comm", r.community()).param("dist", r.district()).param("now", utc(Instant.now()))
                    .update();
            return true;
        } catch (DuplicateKeyException dup) {
            return false;
        }
    }

    @Override
    public Map<String, Long> computeDayMetrics(LocalDate day) {
        Map<String, Long> m = new TreeMap<>();
        // distinct events per canonical name; PROFILE_COMPLETE counts distinct members so repeated saves don't inflate it
        jdbc.sql("select event_name, count(distinct event_id) as events, count(distinct user_hash) as users "
                        + "from analytics_event where event_day = :d group by event_name")
                .param("d", day).query((rs, i) -> {
                    String n = rs.getString("event_name");
                    m.put(n, n.equals(PROFILE_COMPLETE) ? rs.getLong("users") : rs.getLong("events"));
                    return n;
                }).list();
        m.put(ACTIVE_USERS, jdbc.sql("select count(distinct user_hash) from analytics_event where event_day = :d and user_hash <> :none")
                .param("d", day).param("none", NO_ACTOR).query(Long.class).single());
        jdbc.sql("select currency, sum(amount_minor) as total from analytics_event where event_day = :d and event_name = :n "
                        + "and amount_minor is not null group by currency")
                .param("d", day).param("n", PAYMENT_CAPTURED)
                .query((rs, i) -> { m.put(REVENUE_PREFIX + rs.getString("currency"), rs.getLong("total")); return 0; }).list();
        return m;
    }

    @Override
    public Map<LocalDate, Map<String, Long>> storedMetrics(LocalDate from, LocalDate to) {
        Map<LocalDate, Map<String, Long>> out = new TreeMap<>();
        jdbc.sql("select metric_day, metric, metric_value from daily_metric where metric_day between :f and :t")
                .param("f", from).param("t", to)
                .query((rs, i) -> {
                    out.computeIfAbsent(rs.getObject("metric_day", LocalDate.class), k -> new TreeMap<>())
                            .put(rs.getString("metric"), rs.getLong("metric_value"));
                    return 0;
                }).list();
        return out;
    }

    @Override
    @Transactional
    public void replaceStoredMetrics(LocalDate day, Map<String, Long> metrics) {
        jdbc.sql("delete from daily_metric where metric_day = :d").param("d", day).update();
        for (var e : metrics.entrySet()) {
            jdbc.sql("insert into daily_metric (metric_day, metric, metric_value, computed_at) values (:d, :m, :v, :now)")
                    .param("d", day).param("m", e.getKey()).param("v", e.getValue()).param("now", utc(Instant.now())).update();
        }
    }

    @Override
    public long uniqueActiveUsers(LocalDate from, LocalDate to) {
        return jdbc.sql("select count(distinct user_hash) from analytics_event where event_day between :f and :t and user_hash <> :none")
                .param("f", from).param("t", to).param("none", NO_ACTOR).query(Long.class).single();
    }

    @Override
    public Map<String, Set<String>> cohortUsersByEvent(LocalDate from, LocalDate to, List<String> names) {
        Map<String, Set<String>> out = new HashMap<>();
        for (String n : names) {
            Set<String> users = new HashSet<>(jdbc.sql("""
                            select distinct e.user_hash from analytics_event e
                            where e.event_name = :n and e.user_hash <> :none and e.user_hash in
                                  (select r.user_hash from analytics_event r where r.event_name = :reg and r.event_day between :f and :t)""")
                    .param("n", n).param("none", NO_ACTOR).param("reg", REGISTERED).param("f", from).param("t", to)
                    .query(String.class).list());
            out.put(n, users);
        }
        return out;
    }

    @Override
    public List<RevenueRow> revenue(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select event_day, currency, plan, sum(amount_minor) as total, count(distinct event_id) as payments,
                               count(distinct user_hash) as payers
                        from analytics_event where event_name = :n and amount_minor is not null and event_day between :f and :t
                        group by event_day, currency, plan order by event_day""")
                .param("n", PAYMENT_CAPTURED).param("f", from).param("t", to)
                .query((rs, i) -> new RevenueRow(rs.getObject("event_day", LocalDate.class), rs.getString("currency"),
                        rs.getString("plan") == null ? "UNKNOWN" : rs.getString("plan"),
                        rs.getLong("total"), rs.getLong("payments"), rs.getLong("payers"))).list();
    }

    @Override
    public Map<String, LocalDate> registrations(LocalDate from) {
        Map<String, LocalDate> out = new HashMap<>();
        jdbc.sql("select user_hash, min(event_day) as first_day from analytics_event where event_name = :reg and event_day >= :f "
                        + "and user_hash <> :none group by user_hash")
                .param("reg", REGISTERED).param("f", from).param("none", NO_ACTOR)
                .query((rs, i) -> { out.put(rs.getString("user_hash"), rs.getObject("first_day", LocalDate.class)); return 0; }).list();
        return out;
    }

    @Override
    public Map<String, Set<LocalDate>> activityDaysOfCohort(LocalDate from) {
        Map<String, Set<LocalDate>> out = new HashMap<>();
        jdbc.sql("""
                        select distinct e.user_hash, e.event_day from analytics_event e
                        where e.event_name <> :reg and e.user_hash in
                              (select r.user_hash from analytics_event r where r.event_name = :reg and r.event_day >= :f)""")
                .param("reg", REGISTERED).param("f", from)
                .query((rs, i) -> {
                    out.computeIfAbsent(rs.getString("user_hash"), k -> new HashSet<>()).add(rs.getObject("event_day", LocalDate.class));
                    return 0;
                }).list();
        return out;
    }

    @Override
    public Map<LocalDate, Long> dailyCountForTarget(String eventName, String targetHash, LocalDate from, LocalDate to) {
        return daily("select event_day, count(distinct event_id) as c from analytics_event where event_name = :n and target_hash = :h "
                + "and event_day between :f and :t group by event_day", eventName, targetHash, from, to);
    }

    @Override
    public long uniqueActorsForTarget(String eventName, String targetHash, LocalDate from, LocalDate to) {
        return jdbc.sql("select count(distinct user_hash) from analytics_event where event_name = :n and target_hash = :h "
                        + "and event_day between :f and :t and user_hash <> :none")
                .param("n", eventName).param("h", targetHash).param("f", from).param("t", to).param("none", NO_ACTOR)
                .query(Long.class).single();
    }

    @Override
    public Map<LocalDate, Long> dailyCountForActor(String eventName, String userHash, LocalDate from, LocalDate to) {
        return daily("select event_day, count(distinct event_id) as c from analytics_event where event_name = :n and user_hash = :h "
                + "and event_day between :f and :t group by event_day", eventName, userHash, from, to);
    }

    private Map<LocalDate, Long> daily(String sql, String name, String hash, LocalDate from, LocalDate to) {
        Map<LocalDate, Long> out = new TreeMap<>();
        jdbc.sql(sql).param("n", name).param("h", hash).param("f", from).param("t", to)
                .query((rs, i) -> { out.put(rs.getObject("event_day", LocalDate.class), rs.getLong("c")); return 0; }).list();
        return out;
    }
}
