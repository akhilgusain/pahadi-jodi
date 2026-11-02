package in.pahadijodi.admin.api;

import in.pahadijodi.admin.audit.AuditChain;
import in.pahadijodi.admin.audit.AuditLog;
import in.pahadijodi.admin.audit.AuditService;
import in.pahadijodi.admin.client.UserDirectoryClient;
import in.pahadijodi.admin.domain.ApprovalRequest;
import in.pahadijodi.admin.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Back-office endpoints. Role names come from the JWT "roles" claim; ADMIN and SUPER_ADMIN are both super admins. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminControllers {
    static final String SUPER = "hasAnyRole('ADMIN','SUPER_ADMIN')";
    static final String USER_READ = "hasAnyRole('ADMIN','SUPER_ADMIN','SUPPORT','MODERATOR')";
    static final String BAN_MAKER = "hasAnyRole('ADMIN','SUPER_ADMIN','MODERATOR')";
    static final String DASHBOARD = "hasAnyRole('ADMIN','SUPER_ADMIN','FINANCE','MODERATOR','VERIFIER','SUPPORT')";

    public record BanRequest(@NotBlank @Size(max = 500) String reason, boolean permanent, Integer durationDays) {}
    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}
    public record DecisionRequest(@Size(max = 500) String note) {}
    public record BanResponse(String outcome, UUID requestId) {}
    public record ApprovalView(UUID id, String kind, UUID targetUserId, String requestedBy, String reason, boolean permanent,
                               Integer durationDays, String status, String decidedBy, String decisionNote) {
        static ApprovalView of(ApprovalRequest r) {
            return new ApprovalView(r.getId(), r.getKind().name(), r.getTargetUserId(), r.getRequestedBy(), r.getReason(), r.isPermanent(),
                    r.getDurationDays(), r.getStatus().name(), r.getDecidedBy(), r.getDecisionNote());
        }
    }
    public record AuditView(long seq, String actorId, String actorRoles, String action, String targetType, String targetId,
                            String details, String createdAt, String hash) {
        static AuditView of(AuditLog a) {
            return new AuditView(a.getSeq(), a.getActorId(), a.getActorRoles(), a.getAction(), a.getTargetType(), a.getTargetId(),
                    a.getDetails(), a.getCreatedAt().toString(), a.getHash());
        }
    }

    private final UserAdminService users;
    private final DashboardService dashboard;
    private final AuditService audit;

    public AdminControllers(UserAdminService users, DashboardService dashboard, AuditService audit) {
        this.users = users; this.dashboard = dashboard; this.audit = audit;
    }

    // ---- users -------------------------------------------------------------------------------------------------

    @GetMapping("/users")
    @PreAuthorize(USER_READ)
    UserDirectoryClient.PageResult<UserDirectoryClient.UserSummary> search(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return users.search(q, page, size);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize(USER_READ)
    UserDirectoryClient.UserSummary user(@PathVariable UUID id) { return users.get(id); }

    @PostMapping("/users/{id}/bans")
    @PreAuthorize(BAN_MAKER)
    @ResponseStatus(HttpStatus.ACCEPTED)
    BanResponse ban(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth, @Valid @RequestBody BanRequest r) {
        var res = users.ban(Actor.of(jwt, auth), id, r.reason(), r.permanent(), r.durationDays());
        return new BanResponse(res.outcome().name(), res.requestId());
    }

    @PostMapping("/users/{id}/unban")
    @PreAuthorize(BAN_MAKER)
    @ResponseStatus(HttpStatus.ACCEPTED)
    BanResponse unban(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth, @Valid @RequestBody ReasonRequest r) {
        var res = users.unban(Actor.of(jwt, auth), id, r.reason());
        return new BanResponse(res.outcome().name(), res.requestId());
    }

    @PostMapping("/users/{id}/deactivate")
    @PreAuthorize(SUPER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth, @Valid @RequestBody ReasonRequest r) {
        users.deactivate(Actor.of(jwt, auth), id, r.reason());
    }

    @PostMapping("/users/{id}/force-logout")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','SUPPORT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void forceLogout(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        users.forceLogout(Actor.of(jwt, auth), id);
    }

    // ---- maker-checker -----------------------------------------------------------------------------------------

    @GetMapping("/approvals")
    @PreAuthorize(SUPER)
    List<ApprovalView> approvals(@RequestParam(defaultValue = "PENDING") ApprovalRequest.Status status) {
        return users.approvals(status).stream().map(ApprovalView::of).toList();
    }

    @PostMapping("/approvals/{id}/approve")
    @PreAuthorize(SUPER)
    ApprovalView approve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth, @RequestBody(required = false) DecisionRequest d) {
        return ApprovalView.of(users.approve(Actor.of(jwt, auth), id, d == null ? null : d.note()));
    }

    @PostMapping("/approvals/{id}/reject")
    @PreAuthorize(SUPER)
    ApprovalView reject(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth, @RequestBody(required = false) DecisionRequest d) {
        return ApprovalView.of(users.reject(Actor.of(jwt, auth), id, d == null ? null : d.note()));
    }

    // ---- dashboard & audit -------------------------------------------------------------------------------------

    @GetMapping("/dashboard")
    @PreAuthorize(DASHBOARD)
    DashboardService.Dashboard dashboard(@AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return dashboard.build(Actor.of(jwt, auth));
    }

    @GetMapping("/audit")
    @PreAuthorize(SUPER)
    List<AuditView> audit(@RequestParam(defaultValue = "50") int size) {
        return audit.latest(size).stream().map(AuditView::of).toList();
    }

    /** Re-computes the whole hash chain and checks it against the pinned tail. */
    @GetMapping("/audit/verify")
    @PreAuthorize(SUPER)
    AuditChain.Result verify() { return audit.verify(); }
}
