package in.pahadijodi.admin.client;

import java.util.List;
import java.util.Map;

/** Port that gathers KPI numbers from the other services. */
public interface DashboardClient {
    /** Fields are null when a downstream source could not be reached; their names then appear in {@code degraded}. */
    record Kpis(Long totalUsers, Long newUsers7d, Long activeSubscriptions, Map<String, Long> subscriptionsByPlan,
                Long revenueTodayPaise, Long revenueMonthToDatePaise, Long openFraudCases, Long moderationQueue,
                List<String> degraded) {}

    Kpis fetch();
}
