package in.pahadijodi.admin.service;

import in.pahadijodi.admin.audit.AuditService;
import in.pahadijodi.admin.client.UserDirectoryClient;
import in.pahadijodi.admin.client.UserDirectoryClient.UserSummary;
import in.pahadijodi.admin.domain.ApprovalRequest;
import in.pahadijodi.admin.domain.ApprovalRequest.Kind;
import in.pahadijodi.admin.domain.ApprovalRequest.Status;
import in.pahadijodi.admin.domain.Repositories.ApprovalRepository;
import in.pahadijodi.admin.domain.Repositories.RestrictionRepository;
import in.pahadijodi.admin.domain.UserRestriction;
import in.pahadijodi.admin.policy.BanPolicy;
import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {
    public enum Outcome { APPLIED, PENDING_APPROVAL }
    public record BanResult(Outcome outcome, UUID requestId) {}

    private final UserDirectoryClient users;
    private final RestrictionRepository restrictions;
    private final ApprovalRepository approvals;
    private final BanPolicy policy;
    private final AuditService audit;
    private final OutboxService outbox;

    public UserAdminService(UserDirectoryClient users, RestrictionRepository restrictions, ApprovalRepository approvals,
                            BanPolicy policy, AuditService audit, OutboxService outbox) {
        this.users = users; this.restrictions = restrictions; this.approvals = approvals;
        this.policy = policy; this.audit = audit; this.outbox = outbox;
    }

    public UserDirectoryClient.PageResult<UserSummary> search(String q, int page, int size) {
        return users.search(q, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    public UserSummary get(UUID id) { return require(id); }

    // ---- ban / unban with maker-checker ------------------------------------------------------------------------

    @Transactional
    public BanResult ban(Actor actor, UUID userId, String reason, boolean permanent, Integer days) {
        require(userId);
        policy.validate(permanent, days);
        var current = restrictions.findById(userId).orElse(null);
        if (current != null && current.isInForce()) throw new PjException(HttpStatus.CONFLICT, "ALREADY_BANNED", "User is already banned");
        if (approvals.existsByTargetUserIdAndKindAndStatus(userId, Kind.BAN, Status.PENDING)) {
            throw new PjException(HttpStatus.CONFLICT, "APPROVAL_PENDING", "A ban request for this user is already pending approval");
        }
        if (policy.banNeedsApproval(permanent, days)) {
            var req = approvals.save(new ApprovalRequest(Kind.BAN, userId, actor.id(), reason, permanent, permanent ? null : days));
            audit.append(actor.id(), actor.roles(), "BAN_REQUESTED", "USER", userId.toString(), details(reason, permanent, days, "requestId", req.getId()));
            return new BanResult(Outcome.PENDING_APPROVAL, req.getId());
        }
        applyBan(actor.id(), actor.roles(), userId, reason, false, days, null);
        return new BanResult(Outcome.APPLIED, null);
    }

    @Transactional
    public BanResult unban(Actor actor, UUID userId, String reason) {
        require(userId);
        var current = restrictions.findById(userId).filter(UserRestriction::isInForce)
                .orElseThrow(() -> new PjException(HttpStatus.CONFLICT, "NOT_BANNED", "User has no ban in force"));
        if (approvals.existsByTargetUserIdAndKindAndStatus(userId, Kind.UNBAN, Status.PENDING)) {
            throw new PjException(HttpStatus.CONFLICT, "APPROVAL_PENDING", "An unban request for this user is already pending approval");
        }
        if (policy.unbanNeedsApproval(current.isPermanent(), current.getDurationDays())) {
            var req = approvals.save(new ApprovalRequest(Kind.UNBAN, userId, actor.id(), reason, false, null));
            audit.append(actor.id(), actor.roles(), "UNBAN_REQUESTED", "USER", userId.toString(), Map.of("reason", reason, "requestId", req.getId().toString()));
            return new BanResult(Outcome.PENDING_APPROVAL, req.getId());
        }
        applyUnban(actor.id(), actor.roles(), userId, reason, null);
        return new BanResult(Outcome.APPLIED, null);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> approvals(Status status) {
        return approvals.findByStatusOrderByCreatedAtAsc(status);
    }

    @Transactional
    public ApprovalRequest approve(Actor checker, UUID requestId, String note) {
        ApprovalRequest req = pending(requestId);
        policy.assertCanDecide(req.getRequestedBy(), checker.id(), checker.superAdmin());
        if (req.getKind() == Kind.BAN) {
            applyBan(req.getRequestedBy(), "approved-by:" + checker.id(), req.getTargetUserId(), req.getReason(), req.isPermanent(), req.getDurationDays(), requestId);
        } else {
            applyUnban(req.getRequestedBy(), "approved-by:" + checker.id(), req.getTargetUserId(), req.getReason(), requestId);
        }
        req.decide(Status.APPROVED, checker.id(), note);
        audit.append(checker.id(), checker.roles(), "REQUEST_APPROVED", "APPROVAL", requestId.toString(),
                Map.of("kind", req.getKind().name(), "maker", req.getRequestedBy(), "targetUserId", req.getTargetUserId().toString()));
        return req;
    }

    @Transactional
    public ApprovalRequest reject(Actor checker, UUID requestId, String note) {
        ApprovalRequest req = pending(requestId);
        if (!checker.superAdmin()) throw new PjException(HttpStatus.FORBIDDEN, "APPROVER_NOT_AUTHORIZED", "Only a super admin can decide");
        req.decide(Status.REJECTED, checker.id(), note);
        audit.append(checker.id(), checker.roles(), "REQUEST_REJECTED", "APPROVAL", requestId.toString(),
                Map.of("kind", req.getKind().name(), "maker", req.getRequestedBy(), "note", note == null ? "" : note));
        return req;
    }

    // ---- direct actions ----------------------------------------------------------------------------------------

    @Transactional
    public void deactivate(Actor actor, UUID userId, String reason) {
        require(userId);
        users.deactivate(userId);
        outbox.publish("pj.admin.user-deactivated.v1", "UserDeactivated", userId.toString(), Map.of("userId", userId.toString()));
        audit.append(actor.id(), actor.roles(), "USER_DEACTIVATED", "USER", userId.toString(), Map.of("reason", reason == null ? "" : reason));
    }

    @Transactional
    public void forceLogout(Actor actor, UUID userId) {
        require(userId);
        users.forceLogout(userId);
        outbox.publish("pj.admin.force-logout.v1", "ForceLogout", userId.toString(), Map.of("userId", userId.toString()));
        audit.append(actor.id(), actor.roles(), "FORCE_LOGOUT", "USER", userId.toString(), Map.of());
    }

    // ---- internals ---------------------------------------------------------------------------------------------

    private void applyBan(String actorId, String actorRoles, UUID userId, String reason, boolean permanent, Integer days, UUID requestId) {
        var r = restrictions.findById(userId).orElseGet(() -> new UserRestriction(userId));
        r.impose(permanent, days, reason, actorId);
        restrictions.save(r);
        users.setBanned(userId, true, reason, r.getBannedUntil());
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userId", userId.toString());
        p.put("permanent", permanent);
        p.put("until", r.getBannedUntil() == null ? null : r.getBannedUntil().toString());
        p.put("reason", reason);
        outbox.publish("pj.admin.user-banned.v1", "UserBanned", userId.toString(), p);
        audit.append(actorId, actorRoles, "USER_BANNED", "USER", userId.toString(), details(reason, permanent, days, "requestId", requestId));
    }

    private void applyUnban(String actorId, String actorRoles, UUID userId, String reason, UUID requestId) {
        restrictions.findById(userId).ifPresent(r -> { r.lift(); restrictions.save(r); });
        users.setBanned(userId, false, reason, null);
        outbox.publish("pj.admin.user-unbanned.v1", "UserUnbanned", userId.toString(), Map.of("userId", userId.toString(), "reason", reason));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("reason", reason);
        if (requestId != null) d.put("requestId", requestId.toString());
        audit.append(actorId, actorRoles, "USER_UNBANNED", "USER", userId.toString(), d);
    }

    private ApprovalRequest pending(UUID id) {
        ApprovalRequest r = approvals.findById(id).orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Approval request not found"));
        if (r.getStatus() != Status.PENDING) throw new PjException(HttpStatus.CONFLICT, "REQUEST_ALREADY_DECIDED", "Request already decided");
        return r;
    }

    private UserSummary require(UUID id) {
        return users.find(id).orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private static Map<String, Object> details(String reason, boolean permanent, Integer days, String k, Object v) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("reason", reason);
        d.put("permanent", permanent);
        d.put("durationDays", days);
        if (v != null) d.put(k, v.toString());
        return d;
    }
}
