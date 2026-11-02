package in.pahadijodi.admin.service;

import in.pahadijodi.admin.client.DashboardClient;
import in.pahadijodi.admin.client.DashboardClient.Kpis;
import in.pahadijodi.admin.domain.ApprovalRequest.Status;
import in.pahadijodi.admin.domain.Repositories.ApprovalRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    public record Dashboard(Long totalUsers, Long newUsers7d, Long activeSubscriptions, Map<String, Long> subscriptionsByPlan,
                            Long revenueTodayPaise, Long revenueMonthToDatePaise, Long openFraudCases, Long moderationQueue,
                            long pendingApprovals, List<String> degradedSources) {}

    private final DashboardClient client;
    private final ApprovalRepository approvals;

    public DashboardService(DashboardClient client, ApprovalRepository approvals) {
        this.client = client; this.approvals = approvals;
    }

    /** Revenue is only shown to roles with a finance need; everyone else sees it as null. */
    public Dashboard build(Actor actor) {
        Kpis k = client.fetch();
        boolean finance = actor.has("FINANCE");
        return new Dashboard(k.totalUsers(), k.newUsers7d(), k.activeSubscriptions(), k.subscriptionsByPlan(),
                finance ? k.revenueTodayPaise() : null, finance ? k.revenueMonthToDatePaise() : null,
                k.openFraudCases(), k.moderationQueue(), approvals.countByStatus(Status.PENDING), k.degraded());
    }
}
