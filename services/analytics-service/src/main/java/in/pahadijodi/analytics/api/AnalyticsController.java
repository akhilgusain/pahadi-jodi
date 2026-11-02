package in.pahadijodi.analytics.api;

import static in.pahadijodi.common.security.ResourceServerAutoConfiguration.userId;

import in.pahadijodi.analytics.port.EntitlementPort;
import in.pahadijodi.analytics.service.InsightsService;
import in.pahadijodi.analytics.service.MetricsService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final MetricsService metrics;
    private final InsightsService insights;
    private final EntitlementPort entitlements;

    public AnalyticsController(MetricsService metrics, InsightsService insights, EntitlementPort entitlements) {
        this.metrics = metrics;
        this.insights = insights;
        this.entitlements = entitlements;
    }

    /** The caller's own profile views, interests and matches (Premium Plus and VIP). */
    @GetMapping("/me/insights")
    Map<String, Object> myInsights(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "30") int days) {
        if (!entitlements.canViewInsights(jwt)) {
            throw new PjException(HttpStatus.FORBIDDEN, "PLAN_UPGRADE_REQUIRED", "Insights are included in Premium Plus and VIP");
        }
        return insights.insights(userId(jwt), days);
    }

    @GetMapping("/admin/summary")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    Map<String, Object> summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate t = to == null ? metrics.today() : to;
        return metrics.summary(from == null ? t.minusDays(29) : from, t);
    }

    @GetMapping("/admin/funnel")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    Map<String, Object> funnel(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate t = to == null ? metrics.today() : to;
        return metrics.funnel(from == null ? t.minusDays(29) : from, t);
    }

    @GetMapping("/admin/revenue")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    Map<String, Object> revenue(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate t = to == null ? metrics.today() : to;
        return metrics.revenue(from == null ? t.minusDays(29) : from, t);
    }

    @GetMapping("/admin/retention")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    Map<String, Object> retention(@RequestParam(defaultValue = "8") int cohorts) { return metrics.retention(cohorts); }

    /** Force a rollup of one business day (e.g. after a replay). */
    @PostMapping("/admin/rollup")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, Long> rollup(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) { return metrics.rollup(day); }
}
