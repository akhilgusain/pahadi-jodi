package in.pahadijodi.fraud.api;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.fraud.domain.FraudCase;
import in.pahadijodi.fraud.domain.FraudRule;
import in.pahadijodi.fraud.domain.FraudSignal;
import in.pahadijodi.fraud.domain.Repositories.*;
import in.pahadijodi.fraud.engine.Action;
import in.pahadijodi.fraud.engine.RiskEngine.Reason;
import in.pahadijodi.fraud.service.FraudService;
import in.pahadijodi.fraud.service.FraudService.SignalCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fraud")
public class FraudController {
    static final String STAFF_READ = "hasAnyRole('ADMIN','SUPER_ADMIN','MODERATOR')";
    static final String STAFF_WRITE = "hasAnyRole('ADMIN','SUPER_ADMIN')";

    public record SignalRequest(UUID userId, @NotBlank String type, @Size(max = 128) String deviceHash, @Size(max = 64) String ip,
                                @Size(max = 300) String userAgent, @Size(max = 255) String emailDomain, @Size(max = 20) String phone,
                                UUID counterpartId, @Size(max = 4000) String chatText, @Size(max = 8000) String profileText,
                                @Pattern(regexp = "[0-9a-fA-F]{1,16}") String photoHash) {}

    public record AssessmentResponse(UUID evaluationId, UUID userId, int score, Action action, List<Reason> reasons) {}
    public record EvaluationView(UUID id, int score, String action, String trigger, Instant at, List<Reason> reasons) {}
    public record RiskResponse(UUID userId, int score, Action automaticAction, Action manualAction, Action effectiveAction,
                               Instant updatedAt, List<EvaluationView> history) {}
    public record CaseView(UUID id, UUID userId, String status, int score, Action action, String label, String notes,
                           UUID resolvedBy, Instant openedAt, Instant resolvedAt) {
        static CaseView of(FraudCase c) {
            return new CaseView(c.getId(), c.getUserId(), c.getStatus().name(), c.getScore(), c.getAction(),
                    c.getLabel() == null ? null : c.getLabel().name(), c.getNotes(), c.getResolvedBy(), c.getOpenedAt(), c.getResolvedAt());
        }
    }
    public record ResolveRequest(@NotNull FraudCase.Label label, @Size(max = 1000) String notes) {}
    public record RuleView(String code, String description, int weight, boolean enabled, String updatedBy, Instant updatedAt) {
        static RuleView of(FraudRule r) { return new RuleView(r.getCode(), r.getDescription(), r.getWeight(), r.isEnabled(), r.getUpdatedBy(), r.getUpdatedAt()); }
    }
    public record RuleUpdate(boolean enabled, @Min(0) @Max(100) int weight) {}
    public record Summary(long openCases, long resolvedCases) {}

    private final FraudService service;
    private final UserRiskRepository risks;
    private final EvaluationRepository evaluations;
    private final CaseRepository cases;
    private final RuleRepository rules;

    public FraudController(FraudService service, UserRiskRepository risks, EvaluationRepository evaluations,
                           CaseRepository cases, RuleRepository rules) {
        this.service = service; this.risks = risks; this.evaluations = evaluations; this.cases = cases; this.rules = rules;
    }

    /** Ingest a signal (device/IP/e-mail domain...) and get the resulting assessment. Users may only report for themselves. */
    @PostMapping("/signals")
    AssessmentResponse signal(@AuthenticationPrincipal Jwt jwt, Authentication auth, @Valid @RequestBody SignalRequest r) {
        UUID self = UUID.fromString(jwt.getSubject());
        boolean staff = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                || a.getAuthority().equals("ROLE_SUPER_ADMIN") || a.getAuthority().equals("ROLE_SERVICE"));
        UUID target = r.userId() == null ? self : r.userId();
        if (!target.equals(self) && !staff) {
            throw new PjException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Cannot report signals for another user");
        }
        FraudSignal.Type type;
        try { type = FraudSignal.Type.valueOf(r.type().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "UNKNOWN_SIGNAL_TYPE", "Unknown signal type"); }
        Long photo = r.photoHash() == null ? null : Long.parseUnsignedLong(r.photoHash(), 16);
        var a = service.ingest(new SignalCommand(target, type, r.deviceHash(), r.ip(), r.userAgent(), r.emailDomain(), r.phone(),
                r.counterpartId(), r.chatText(), r.profileText(), photo, "API"));
        return new AssessmentResponse(a.evaluationId(), a.userId(), a.score(), a.action(), a.reasons());
    }

    @GetMapping("/users/{id}/risk")
    @PreAuthorize(STAFF_READ)
    RiskResponse risk(@PathVariable UUID id) {
        var r = risks.findById(id).orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "NO_RISK_RECORD", "No risk assessment for this user"));
        var history = evaluations.findByUserIdOrderByCreatedAtDesc(id, PageRequest.of(0, 10)).stream()
                .map(e -> new EvaluationView(e.getId(), e.getScore(), e.getAction(), e.getTriggerType(), e.getCreatedAt(), service.parseReasons(e.getReasons())))
                .toList();
        return new RiskResponse(id, r.getScore(), r.getAction(), r.getManualAction(), r.effectiveAction(), r.getUpdatedAt(), history);
    }

    @GetMapping("/admin/cases")
    @PreAuthorize(STAFF_READ)
    List<CaseView> cases(@RequestParam(defaultValue = "OPEN") FraudCase.Status status, @RequestParam(defaultValue = "50") int size) {
        return cases.findByStatusOrderByOpenedAtDesc(status, PageRequest.of(0, Math.min(Math.max(size, 1), 200))).stream().map(CaseView::of).toList();
    }

    @PostMapping("/admin/cases/{id}/resolve")
    @PreAuthorize(STAFF_WRITE)
    CaseView resolve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ResolveRequest r) {
        return CaseView.of(service.resolve(id, r.label(), r.notes(), UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/admin/rules")
    @PreAuthorize(STAFF_READ)
    List<RuleView> rules() {
        return rules.findAll().stream().sorted(java.util.Comparator.comparing(FraudRule::getCode)).map(RuleView::of).toList();
    }

    @PutMapping("/admin/rules/{code}")
    @PreAuthorize(STAFF_WRITE)
    RuleView updateRule(@PathVariable String code, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RuleUpdate u) {
        return RuleView.of(service.updateRule(code.toUpperCase(), u.enabled(), u.weight(), jwt.getSubject()));
    }

    @GetMapping("/admin/summary")
    @PreAuthorize(STAFF_READ)
    Summary summary() {
        return new Summary(cases.countByStatus(FraudCase.Status.OPEN), cases.countByStatus(FraudCase.Status.RESOLVED));
    }
}
