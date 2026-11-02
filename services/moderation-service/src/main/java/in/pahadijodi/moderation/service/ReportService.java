package in.pahadijodi.moderation.service;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.moderation.domain.*;
import in.pahadijodi.moderation.domain.Enums.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private static final int CROWD_THRESHOLD = 3;   // distinct reporters within 7 days -> top priority

    private final UserReportRepository reports;
    private final ModerationCaseRepository cases;
    private final ModerationService moderation;

    public ReportService(UserReportRepository reports, ModerationCaseRepository cases, ModerationService moderation) {
        this.reports = reports;
        this.cases = cases;
        this.moderation = moderation;
    }

    @Transactional
    public UserReport report(UUID reporter, UUID target, ReportReason reason, String details, String evidenceRef) {
        if (reporter.equals(target)) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "SELF_REPORT", "You cannot report yourself");
        if (reports.existsByReporterIdAndTargetUserIdAndCreatedAtAfter(reporter, target, Instant.now().minus(Duration.ofHours(24)))) {
            throw new PjException(HttpStatus.CONFLICT, "ALREADY_REPORTED", "You already reported this user in the last 24 hours");
        }
        UserReport r = reports.save(new UserReport(reporter, target, reason, details, evidenceRef));
        String label = reason.name();
        int priority = ModerationService.SEVERE.contains(label) ? 2 : 3;
        ModerationCase c = moderation.openCase(ContentType.REPORT, r.getId().toString(), target, "REPORT", label, priority,
                details == null ? null : (details.length() > 280 ? details.substring(0, 280) : details));
        r.attach(c.getId());

        long distinct = reports.countDistinctReporters(target, Instant.now().minus(Duration.ofDays(7)));
        if (distinct >= CROWD_THRESHOLD) {
            List<ModerationCase> open = cases.findBySubjectUserIdAndContentTypeAndStatusIn(target, ContentType.REPORT,
                    List.of(CaseStatus.OPEN, CaseStatus.IN_REVIEW));
            open.forEach(x -> x.bumpPriority(1));
        }
        return r;
    }
}
