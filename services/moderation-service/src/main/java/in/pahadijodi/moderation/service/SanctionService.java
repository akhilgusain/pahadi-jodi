package in.pahadijodi.moderation.service;

import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.moderation.domain.*;
import in.pahadijodi.moderation.domain.Enums.AccountStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Strike ladder: active strike points (expire after N days) map to
 * NONE -> 7 day suspension -> 30 day suspension -> permanent ban. Manual (admin) bans are never
 * overridden by the ladder.
 */
@Service
public class SanctionService {
    public record StatusView(UUID userId, String status, Instant until, long activePoints, String reason) {}

    private enum Rung { NONE, TEMP_7D, TEMP_30D, PERMANENT }

    private final UserStrikeRepository strikes;
    private final UserStatusRepository statuses;
    private final OutboxService outbox;
    private final AuditService audit;
    private final int expiryDays, p7, p30, pPerm;

    public SanctionService(UserStrikeRepository strikes, UserStatusRepository statuses, OutboxService outbox, AuditService audit,
                           @Value("${pj.moderation.strikes.expiry-days}") int expiryDays,
                           @Value("${pj.moderation.strikes.temp-7d-points}") int p7,
                           @Value("${pj.moderation.strikes.temp-30d-points}") int p30,
                           @Value("${pj.moderation.strikes.permanent-points}") int pPerm) {
        this.strikes = strikes;
        this.statuses = statuses;
        this.outbox = outbox;
        this.audit = audit;
        this.expiryDays = expiryDays;
        this.p7 = p7;
        this.p30 = p30;
        this.pPerm = pPerm;
    }

    @Transactional
    public void addStrike(UUID userId, UUID caseId, int points, String reason) {
        strikes.save(new UserStrike(userId, caseId, points, reason));
        audit.append(caseId, "system", "STRIKE_ADDED", "user=" + userId + " points=" + points + " reason=" + reason);
        recompute(userId, false);
    }

    /** Called when an appeal overturns a decision: strikes of that case no longer count. */
    @Transactional
    public void revokeStrikesOfCase(UUID caseId) {
        UUID user = null;
        for (UserStrike s : strikes.findByCaseIdAndRevokedFalse(caseId)) {
            s.revoke();
            user = s.getUserId();
        }
        if (user != null) recompute(user, true);
    }

    @Transactional
    public void recompute(UUID userId, boolean allowLift) {
        long pts = activePoints(userId);
        Rung desired = pts >= pPerm ? Rung.PERMANENT : pts >= p30 ? Rung.TEMP_30D : pts >= p7 ? Rung.TEMP_7D : Rung.NONE;
        UserStatus s = statuses.findById(userId).orElseGet(() -> new UserStatus(userId));
        AccountStatus eff = s.effective();
        if ("MANUAL".equals(s.getSource()) && eff != AccountStatus.ACTIVE) return;   // admin decisions win

        Rung current = eff == AccountStatus.BANNED ? Rung.PERMANENT
                : eff == AccountStatus.SUSPENDED ? (s.getUntil() != null && Duration.between(Instant.now(), s.getUntil()).toDays() >= 8 ? Rung.TEMP_30D : Rung.TEMP_7D)
                : Rung.NONE;
        if (desired.ordinal() > current.ordinal() || (allowLift && desired.ordinal() < current.ordinal())) {
            apply(s, desired, "strike points=" + pts);
        }
    }

    private void apply(UserStatus s, Rung r, String reason) {
        switch (r) {
            case NONE -> { s.set(AccountStatus.ACTIVE, null, "STRIKES", reason); publish(s, "LIFTED"); }
            case TEMP_7D -> { s.set(AccountStatus.SUSPENDED, Instant.now().plus(Duration.ofDays(7)), "STRIKES", reason); publish(s, "TEMPORARY"); }
            case TEMP_30D -> { s.set(AccountStatus.SUSPENDED, Instant.now().plus(Duration.ofDays(30)), "STRIKES", reason); publish(s, "TEMPORARY"); }
            case PERMANENT -> { s.set(AccountStatus.BANNED, null, "STRIKES", reason); publish(s, "PERMANENT"); }
        }
        statuses.save(s);
        audit.append(null, "system", "STATUS_" + s.getStatus(), "user=" + s.getUserId() + " until=" + s.getUntil() + " " + reason);
    }

    // ---- admin / moderator actions ----------------------------------------------------------------

    @Transactional
    public StatusView ban(UUID actor, boolean actorIsAdmin, UUID userId, boolean permanent, Integer days, String reason) {
        if (permanent && !actorIsAdmin) {
            throw new PjException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Only an admin can ban permanently");
        }
        int d = days == null ? 7 : days;
        if (!permanent && (d < 1 || d > 90)) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DURATION", "Suspension must be 1-90 days");
        }
        UserStatus s = statuses.findById(userId).orElseGet(() -> new UserStatus(userId));
        if (permanent) s.set(AccountStatus.BANNED, null, "MANUAL", reason);
        else s.set(AccountStatus.SUSPENDED, Instant.now().plus(Duration.ofDays(d)), "MANUAL", reason);
        statuses.save(s);
        publish(s, permanent ? "PERMANENT" : "TEMPORARY");
        audit.append(null, actor.toString(), permanent ? "BAN" : "SUSPEND", "user=" + userId + " reason=" + reason);
        return view(userId);
    }

    @Transactional
    public StatusView unban(UUID actor, UUID userId, String reason) {
        UserStatus s = statuses.findById(userId).orElseGet(() -> new UserStatus(userId));
        s.set(AccountStatus.ACTIVE, null, "MANUAL", reason);
        statuses.save(s);
        publish(s, "LIFTED");
        audit.append(null, actor.toString(), "UNBAN", "user=" + userId + " reason=" + reason);
        return view(userId);
    }

    @Transactional(readOnly = true)
    public StatusView view(UUID userId) {
        long pts = activePoints(userId);
        return statuses.findById(userId)
                .map(s -> new StatusView(userId, s.effective().name(), s.effective() == AccountStatus.ACTIVE ? null : s.getUntil(), pts, s.getReason()))
                .orElse(new StatusView(userId, AccountStatus.ACTIVE.name(), null, pts, null));
    }

    @Transactional(readOnly = true)
    public AccountStatus effectiveStatus(UUID userId) {
        return statuses.findById(userId).map(UserStatus::effective).orElse(AccountStatus.ACTIVE);
    }

    private long activePoints(UUID userId) {
        return strikes.activePoints(userId, Instant.now().minus(Duration.ofDays(expiryDays)));
    }

    private void publish(UserStatus s, String kind) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", s.getUserId().toString());
        payload.put("kind", kind);                     // TEMPORARY | PERMANENT | LIFTED
        payload.put("until", s.getUntil() == null ? null : s.getUntil().toString());
        payload.put("reason", s.getReason());
        payload.put("source", s.getSource());
        outbox.publish("pj.user.banned.v1", "UserSanctionChanged", s.getUserId().toString(), payload);
    }
}
