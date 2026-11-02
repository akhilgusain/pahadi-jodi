package in.pahadijodi.moderation.service;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.moderation.domain.*;
import in.pahadijodi.moderation.domain.Enums.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppealService {
    private final AppealRepository appeals;
    private final ModerationCaseRepository cases;
    private final SanctionService sanctions;
    private final AuditService audit;

    public AppealService(AppealRepository appeals, ModerationCaseRepository cases, SanctionService sanctions, AuditService audit) {
        this.appeals = appeals;
        this.cases = cases;
        this.sanctions = sanctions;
        this.audit = audit;
    }

    /** A user can appeal a decision about their own content, or (without a case) their current suspension/ban. */
    @Transactional
    public Appeal appeal(UUID userId, UUID caseId, String message) {
        if (caseId != null) {
            ModerationCase c = cases.findById(caseId).filter(x -> userId.equals(x.getSubjectUserId()))
                    .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Case not found"));
            boolean actionable = c.getDecision() == Decision.BLOCK || c.getResolution() == Resolution.REJECT;
            if (!actionable) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "NOTHING_TO_APPEAL", "No adverse decision on this case");
            if (appeals.existsByUserIdAndCaseIdAndStatus(userId, caseId, AppealStatus.OPEN)) {
                throw new PjException(HttpStatus.CONFLICT, "APPEAL_EXISTS", "An appeal is already open");
            }
        } else if (sanctions.effectiveStatus(userId) == AccountStatus.ACTIVE) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "NOTHING_TO_APPEAL", "Your account is not restricted");
        }
        Appeal a = appeals.save(new Appeal(userId, caseId, message));
        audit.append(caseId, userId.toString(), "APPEAL_OPENED", message.length() > 200 ? message.substring(0, 200) : message);
        return a;
    }

    @Transactional(readOnly = true)
    public List<Appeal> mine(UUID userId) {
        return appeals.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Appeal> open() {
        return appeals.findByStatusOrderByCreatedAtAsc(AppealStatus.OPEN);
    }

    @Transactional
    public Appeal decide(UUID admin, UUID appealId, boolean overturn, String note) {
        Appeal a = appeals.findById(appealId).filter(x -> x.getStatus() == AppealStatus.OPEN)
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "APPEAL_NOT_FOUND", "No open appeal with that id"));
        a.decide(admin, overturn, note);
        audit.append(a.getCaseId(), admin.toString(), overturn ? "APPEAL_OVERTURNED" : "APPEAL_UPHELD", note);
        if (overturn) {
            if (a.getCaseId() != null) sanctions.revokeStrikesOfCase(a.getCaseId());
            else sanctions.unban(admin, a.getUserId(), "appeal overturned");
        }
        return a;
    }
}
