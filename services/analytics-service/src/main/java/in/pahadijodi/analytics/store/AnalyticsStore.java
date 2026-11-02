package in.pahadijodi.analytics.store;

import in.pahadijodi.analytics.model.FactRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Port for the analytical store. Today: SQL on this service's own schema ({@link JdbcAnalyticsStore}). At scale a
 * ClickHouse adapter (MergeTree on (event_name, event_day), ingest via the Kafka table engine or ClickPipes)
 * implements the same interface, the controllers and services do not change. See docs/04 section 15.
 */
public interface AnalyticsStore {

    /** @return false if the row was a duplicate (same eventId + event name + actor) */
    boolean append(FactRow row);

    /** Live metrics for one business day: event counts (distinct events), ACTIVE_USERS, REVENUE_&lt;CUR&gt;. */
    Map<String, Long> computeDayMetrics(LocalDate day);

    Map<LocalDate, Map<String, Long>> storedMetrics(LocalDate from, LocalDate to);

    void replaceStoredMetrics(LocalDate day, Map<String, Long> metrics);

    long uniqueActiveUsers(LocalDate from, LocalDate to);

    /** Users per event name among those who registered in [from, to]: name -> set of user hashes. */
    Map<String, Set<String>> cohortUsersByEvent(LocalDate from, LocalDate to, List<String> eventNames);

    List<RevenueRow> revenue(LocalDate from, LocalDate to);

    /** user hash -> first registration day, for registrations on/after {@code from}. */
    Map<String, LocalDate> registrations(LocalDate from);

    /** For users registered on/after {@code from}: days with any non-registration activity. */
    Map<String, Set<LocalDate>> activityDaysOfCohort(LocalDate from);

    Map<LocalDate, Long> dailyCountForTarget(String eventName, String targetHash, LocalDate from, LocalDate to);

    long uniqueActorsForTarget(String eventName, String targetHash, LocalDate from, LocalDate to);

    Map<LocalDate, Long> dailyCountForActor(String eventName, String userHash, LocalDate from, LocalDate to);

    record RevenueRow(LocalDate day, String currency, String plan, long amountMinor, long payments, long payers) {}
}
