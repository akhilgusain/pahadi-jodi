package in.pahadijodi.moderation.api;

import in.pahadijodi.common.security.ResourceServerAutoConfiguration;
import in.pahadijodi.moderation.domain.*;
import in.pahadijodi.moderation.domain.Enums.*;
import in.pahadijodi.moderation.port.ContentClassifier.Label;
import in.pahadijodi.moderation.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/moderation")
public class ModerationController {
    // ---- request / response shapes ----------------------------------------------------------------------
    public record CheckRequest(@NotNull ContentType contentType, @Size(max = 5000) String text, String refId, UUID subjectUserId) {}
    public record CheckResponse(Decision decision, List<Label> labels, double score, UUID caseId) {}
    public record ReportRequest(@NotNull UUID targetUserId, @NotNull ReportReason reason, @Size(max = 2000) String details,
                                @Size(max = 200) String evidenceRef) {}
    public record ReportResponse(UUID id, UUID caseId) {}
    public record DecisionRequest(@NotNull Resolution decision, @Size(max = 500) String note) {}
    public record BanRequest(boolean permanent, Integer days, @NotBlank @Size(max = 300) String reason) {}
    public record UnbanRequest(@NotBlank @Size(max = 300) String reason) {}
    public record AppealRequest(UUID caseId, @NotBlank @Size(max = 2000) String message) {}
    public record AppealDecision(boolean overturn, @Size(max = 500) String note) {}

    public record CaseView(UUID id, ContentType contentType, String refId, UUID subjectUserId, String source, Decision decision,
                           String labels, double score, CaseStatus status, int priority, UUID assignedTo, Instant dueAt,
                           boolean overdue, Resolution resolution, String note, String excerpt, Instant createdAt) {
        static CaseView of(ModerationCase c) {
            return new CaseView(c.getId(), c.getContentType(), c.getRefId(), c.getSubjectUserId(), c.getSource(), c.getDecision(),
                    c.getLabels(), c.getScore(), c.getStatus(), c.getPriority(), c.getAssignedTo(), c.getDueAt(),
                    !c.isFinal() && c.getDueAt().isBefore(Instant.now()), c.getResolution(), c.getNote(), c.getExcerpt(), c.getCreatedAt());
        }
    }

    public record AppealView(UUID id, UUID userId, UUID caseId, String message, AppealStatus status, String decisionNote, Instant createdAt) {
        static AppealView of(Appeal a) {
            return new AppealView(a.getId(), a.getUserId(), a.getCaseId(), a.getMessage(), a.getStatus(), a.getDecisionNote(), a.getCreatedAt());
        }
    }

    public record AuditView(Long seq, UUID caseId, String actor, String action, String detail, Instant createdAt, String hash) {}

    private final ModerationService moderation;
    private final ReportService reports;
    private final SanctionService sanctions;
    private final AppealService appeals;
    private final AuditService audit;

    public ModerationController(ModerationService moderation, ReportService reports, SanctionService sanctions,
                                AppealService appeals, AuditService audit) {
        this.moderation = moderation;
        this.reports = reports;
        this.sanctions = sanctions;
        this.appeals = appeals;
        this.audit = audit;
    }

    private static UUID uid(Jwt jwt) { return ResourceServerAutoConfiguration.userId(jwt); }

    private static boolean has(Authentication a, String role) {
        return a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_" + role));
    }

    // ---- automated check (called inline by chat/profile services, or by the web app before posting) ------------

    @PostMapping("/check")
    CheckResponse check(@AuthenticationPrincipal Jwt jwt, Authentication auth, @Valid @RequestBody CheckRequest r) {
        if (r.contentType() == ContentType.IMAGE ? r.refId() == null || r.refId().isBlank() : r.text() == null || r.text().isBlank()) {
            throw new in.pahadijodi.common.web.ProblemAdvice.PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REQUEST",
                    r.contentType() == ContentType.IMAGE ? "refId is required for IMAGE" : "text is required");
        }
        boolean trusted = has(auth, "SERVICE") || has(auth, "ADMIN") || has(auth, "MODERATOR");
        UUID subject = trusted && r.subjectUserId() != null ? r.subjectUserId() : uid(jwt);
        var v = moderation.check(subject, r.contentType(), r.text(), r.refId(), "API");
        return new CheckResponse(v.decision(), v.labels(), v.score(), v.caseId());
    }

    // ---- reports ----------------------------------------------------------------------------------------------

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    ReportResponse report(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReportRequest r) {
        var rep = reports.report(uid(jwt), r.targetUserId(), r.reason(), r.details(), r.evidenceRef());
        return new ReportResponse(rep.getId(), rep.getCaseId());
    }

    // ---- review queue -----------------------------------------------------------------------------------------

    @GetMapping("/cases")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    List<CaseView> queue(@RequestParam(required = false) List<CaseStatus> status,
                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var st = status == null || status.isEmpty() ? List.of(CaseStatus.OPEN, CaseStatus.IN_REVIEW, CaseStatus.ESCALATED) : status;
        return moderation.queue(st, page, size).stream().map(CaseView::of).toList();
    }

    @GetMapping("/cases/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    CaseView one(@PathVariable UUID id) { return CaseView.of(moderation.get(id)); }

    @PostMapping("/cases/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    CaseView assign(@AuthenticationPrincipal Jwt jwt, Authentication auth, @PathVariable UUID id) {
        return CaseView.of(moderation.assign(id, uid(jwt), has(auth, "ADMIN")));
    }

    @PostMapping("/cases/{id}/decision")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    CaseView decide(@AuthenticationPrincipal Jwt jwt, Authentication auth, @PathVariable UUID id, @Valid @RequestBody DecisionRequest r) {
        return CaseView.of(moderation.decide(id, uid(jwt), has(auth, "ADMIN"), r.decision(), r.note()));
    }

    // ---- sanctions -----------------------------------------------------------------------------------------------

    @GetMapping("/users/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','SERVICE') or #id.toString() == authentication.name")
    SanctionService.StatusView status(@PathVariable UUID id) { return sanctions.view(id); }

    @PostMapping("/users/{id}/ban")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    SanctionService.StatusView ban(@AuthenticationPrincipal Jwt jwt, Authentication auth, @PathVariable UUID id, @Valid @RequestBody BanRequest r) {
        return sanctions.ban(uid(jwt), has(auth, "ADMIN"), id, r.permanent(), r.days(), r.reason());
    }

    @PostMapping("/users/{id}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    SanctionService.StatusView unban(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody UnbanRequest r) {
        return sanctions.unban(uid(jwt), id, r.reason());
    }

    // ---- appeals -----------------------------------------------------------------------------------------------------

    @PostMapping("/appeals")
    @ResponseStatus(HttpStatus.CREATED)
    AppealView appeal(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AppealRequest r) {
        return AppealView.of(appeals.appeal(uid(jwt), r.caseId(), r.message()));
    }

    @GetMapping("/appeals/me")
    List<AppealView> myAppeals(@AuthenticationPrincipal Jwt jwt) {
        return appeals.mine(uid(jwt)).stream().map(AppealView::of).toList();
    }

    @GetMapping("/appeals")
    @PreAuthorize("hasRole('ADMIN')")
    List<AppealView> openAppeals() { return appeals.open().stream().map(AppealView::of).toList(); }

    @PostMapping("/appeals/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    AppealView decideAppeal(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody AppealDecision d) {
        return AppealView.of(appeals.decide(uid(jwt), id, d.overturn(), d.note()));
    }

    // ---- audit -----------------------------------------------------------------------------------------------------------

    @GetMapping("/audit/verify")
    @PreAuthorize("hasRole('ADMIN')")
    AuditService.Verification verify() { return audit.verify(); }

    @GetMapping("/cases/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    List<AuditView> caseAudit(@PathVariable UUID id) {
        return audit.forCase(id).stream()
                .map(a -> new AuditView(a.getSeq(), a.getCaseId(), a.getActor(), a.getAction(), a.getDetail(), a.getCreatedAt(), a.getHash())).toList();
    }
}
