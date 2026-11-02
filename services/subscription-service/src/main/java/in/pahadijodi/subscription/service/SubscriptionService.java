package in.pahadijodi.subscription.service;

import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.subscription.domain.*;
import in.pahadijodi.subscription.domain.PlanEntitlement.Kind;
import in.pahadijodi.subscription.domain.PlanEntitlement.Period;
import in.pahadijodi.subscription.domain.Repos.*;
import in.pahadijodi.subscription.domain.Subscription.Status;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {
    private static final java.util.concurrent.locks.ReentrantLock[] STRIPES = java.util.stream.Stream
            .generate(java.util.concurrent.locks.ReentrantLock::new).limit(64)
            .toArray(java.util.concurrent.locks.ReentrantLock[]::new);

    public static final String FREE = "FREE";
    public static final String T_ACTIVATED = "pj.subscription.activated.v1";
    public static final String T_GRACE = "pj.subscription.grace-started.v1";
    public static final String T_EXPIRED = "pj.subscription.expired.v1";
    public static final String T_CANCELED = "pj.subscription.canceled.v1";
    public static final String T_ENTITLEMENT_CHANGED = "pj.subscription.entitlement-changed.v1";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);

    // ---- views ----
    public record EntitlementView(String key, Kind kind, boolean enabled, Long limit, Period period, String variant,
                                  long used, Long remaining, Instant resetsAt) {}
    public record SubscriptionView(String planCode, String status, Instant startedAt, Instant currentPeriodEnd,
                                   Instant graceEndsAt, boolean cancelAtPeriodEnd, List<EntitlementView> entitlements) {}
    public record PlanView(String code, String name, long priceInrMinor, long priceUsdMinor, int durationDays,
                           List<EntitlementView> entitlements) {}
    public record CheckResult(boolean allowed, Long remaining, String reason, String upgradeTo) {}

    private final Plans plans;
    private final Entitlements entitlements;
    private final Subscriptions subscriptions;
    private final AppliedPayments applied;
    private final UsageCounters counters;
    private final CounterRows counterRows;
    private final OutboxService outbox;
    private final long graceDays;

    public SubscriptionService(Plans plans, Entitlements entitlements, Subscriptions subscriptions, AppliedPayments applied,
                               UsageCounters counters, CounterRows counterRows, OutboxService outbox,
                               @Value("${pj.subscription.grace-days:3}") long graceDays) {
        this.plans = plans; this.entitlements = entitlements; this.subscriptions = subscriptions; this.applied = applied;
        this.counters = counters; this.counterRows = counterRows; this.outbox = outbox; this.graceDays = graceDays;
    }

    // ================= catalogue =================
    @Transactional(readOnly = true)
    public List<PlanView> catalogue() {
        List<PlanView> out = new ArrayList<>();
        for (Plan p : plans.findByActiveTrueOrderByTierRank()) {
            List<EntitlementView> es = entitlements.findByTier(p.getCode()).stream()
                    .sorted(Comparator.comparing(PlanEntitlement::getKey))
                    .map(e -> view(e, 0, Instant.now())).toList();
            out.add(new PlanView(p.getCode(), p.getName(), p.getPriceInrMinor(), p.getPriceUsdMinor(), p.getDurationDays(), es));
        }
        return out;
    }

    // ================= current subscription =================
    /** Effective tier: the plan while ACTIVE/GRACE, otherwise FREE (no row needed: users are FREE lazily). */
    @Transactional(readOnly = true)
    public String effectiveTier(UUID userId) {
        return subscriptions.findByUserId(userId).filter(Subscription::grantsEntitlements)
                .map(Subscription::getPlanCode).orElse(FREE);
    }

    @Transactional(readOnly = true)
    public SubscriptionView me(UUID userId) {
        Optional<Subscription> s = subscriptions.findByUserId(userId);
        String tier = s.filter(Subscription::grantsEntitlements).map(Subscription::getPlanCode).orElse(FREE);
        List<EntitlementView> es = entitlementsFor(userId, tier);
        if (s.isEmpty()) return new SubscriptionView(FREE, "ACTIVE", null, null, null, false, es);
        Subscription x = s.get();
        String status = x.grantsEntitlements() ? x.getStatus().name() : x.getStatus().name();
        return new SubscriptionView(tier, status, x.getStartedAt(), x.getCurrentPeriodEnd(), x.getGraceEndsAt(),
                x.isCancelAtPeriodEnd(), es);
    }

    @Transactional(readOnly = true)
    public List<EntitlementView> entitlementsFor(UUID userId, String tier) {
        Instant now = Instant.now();
        Map<String, Long> used = new HashMap<>();
        counters.findForPeriods(userId, List.of(DAY.format(now), MONTH.format(now), "ALL"))
                .forEach(c -> used.put(c.getId(), c.getUsed()));
        return entitlements.findByTier(tier).stream().sorted(Comparator.comparing(PlanEntitlement::getKey))
                .map(e -> view(e, used.getOrDefault(counterId(userId, e.getKey(), periodKey(e.getPeriodUnit(), now)), 0L), now))
                .toList();
    }

    private static EntitlementView view(PlanEntitlement e, long used, Instant now) {
        boolean quota = e.getKind() == Kind.QUOTA || e.getKind() == Kind.CAP;
        Long remaining = (quota && e.isEnabled() && e.getQuotaLimit() != null) ? Math.max(0, e.getQuotaLimit() - used) : null;
        return new EntitlementView(e.getKey(), e.getKind(), e.isEnabled(), e.getQuotaLimit(), e.getPeriodUnit(),
                e.getVariant(), used, remaining, resetsAt(e.getPeriodUnit(), now));
    }

    // ================= activation / renewal =================
    /** Idempotent by paymentId: replays of the same payment event are no-ops. Returns false if already applied. */
    @Transactional
    public boolean activate(UUID userId, String planCode, String billingCycle, String paymentId) {
        return activate(userId, planCode, billingCycle, paymentId, Instant.now());
    }

    @Transactional
    public boolean activate(UUID userId, String planCode, String billingCycle, String paymentId, Instant now) {
        Plan plan = plans.findById(planCode)
                .filter(p -> p.isActive() && !FREE.equals(p.getCode()))
                .orElseThrow(() -> new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "UNKNOWN_PLAN", "Unknown or non-purchasable plan: " + planCode));
        if (paymentId != null && applied.existsById(paymentId)) return false;

        int days = "MONTHLY".equalsIgnoreCase(billingCycle) ? 30 : plan.getDurationDays();
        Subscription s = subscriptions.findByUserId(userId).orElseGet(() -> new Subscription(userId, planCode, now));
        boolean renewal = s.grantsEntitlements() && s.getPlanCode().equals(planCode);
        Instant start = renewal ? s.getStartedAt() : now;
        Instant base = renewal && s.getCurrentPeriodEnd().isAfter(now) ? s.getCurrentPeriodEnd() : now;
        String before = s.grantsEntitlements() ? s.getPlanCode() : FREE;
        try {
            s.activate(planCode, start, base.plus(Duration.ofDays(days)), paymentId);
        } catch (IllegalStateException e) {
            throw new PjException(HttpStatus.CONFLICT, "INVALID_STATE", e.getMessage());
        }
        subscriptions.save(s);
        if (paymentId != null) applied.save(new AppliedPayment(paymentId, userId));

        outbox.publish(T_ACTIVATED, "SubscriptionActivated", userId.toString(), mapOf(
                "userId", userId.toString(), "planCode", planCode, "renewal", renewal,
                "currentPeriodEnd", s.getCurrentPeriodEnd().toString(), "paymentId", paymentId));
        if (!before.equals(planCode)) entitlementChanged(userId, planCode);
        return true;
    }

    // ================= cancellation =================
    @Transactional
    public SubscriptionView cancelAtPeriodEnd(UUID userId, boolean cancel) {
        Subscription s = subscriptions.findByUserId(userId).filter(Subscription::grantsEntitlements)
                .orElseThrow(() -> new PjException(HttpStatus.CONFLICT, "NO_ACTIVE_SUBSCRIPTION", "No active subscription"));
        s.setCancelAtPeriodEnd(cancel);
        subscriptions.save(s);
        return me(userId);
    }

    /** Immediate cancellation (refund / admin / fraud). Entitlements drop to FREE straight away. */
    @Transactional
    public void cancelNow(UUID userId, String reason) {
        subscriptions.findByUserId(userId).filter(Subscription::grantsEntitlements).ifPresent(s -> {
            s.cancelNow();
            subscriptions.save(s);
            outbox.publish(T_CANCELED, "SubscriptionCanceled", userId.toString(), mapOf("userId", userId.toString(), "reason", reason));
            entitlementChanged(userId, FREE);
        });
    }

    // ================= expiry sweep =================
    /** ACTIVE past period end -> GRACE (3 days) or EXPIRED if cancel_at_period_end; GRACE past its end -> EXPIRED. */
    @Transactional
    public int processExpiries(Instant now) {
        int changed = 0;
        for (Subscription s : subscriptions.findByStatusAndCurrentPeriodEndBefore(Status.ACTIVE, now)) {
            if (s.isCancelAtPeriodEnd()) {
                expire(s);
            } else {
                s.enterGrace(s.getCurrentPeriodEnd().plus(Duration.ofDays(graceDays)));
                subscriptions.save(s);
                // payment-service retries the charge during grace; notification-service nudges the user
                outbox.publish(T_GRACE, "SubscriptionGraceStarted", s.getUserId().toString(),
                        mapOf("userId", s.getUserId().toString(), "planCode", s.getPlanCode(), "graceEndsAt", s.getGraceEndsAt().toString()));
            }
            changed++;
        }
        for (Subscription s : subscriptions.findByStatusAndGraceEndsAtBefore(Status.GRACE, now)) {
            expire(s);
            changed++;
        }
        return changed;
    }

    private void expire(Subscription s) {
        String plan = s.getPlanCode();
        s.expire();
        subscriptions.save(s);
        outbox.publish(T_EXPIRED, "SubscriptionExpired", s.getUserId().toString(),
                mapOf("userId", s.getUserId().toString(), "planCode", plan));
        entitlementChanged(s.getUserId(), FREE);
    }

    private void entitlementChanged(UUID userId, String tier) {
        outbox.publish(T_ENTITLEMENT_CHANGED, "EntitlementChanged", userId.toString(),
                mapOf("userId", userId.toString(), "tier", tier));
    }

    // ================= entitlement check =================
    /**
     * Single source of truth for "may this user do X?". QUOTA keys are consumed atomically in the DB
     * (UPDATE ... WHERE used + n <= limit); CAP keys compare a caller-supplied current count to the ceiling;
     * FLAG keys are plain booleans. Denials return allowed=false (HTTP 200) with a reason and the cheapest upgrade.
     */
    // NOT @Transactional on purpose: the counter row is created and then locked in separate short transactions
    // (CounterRows). Holding an outer transaction while asking for a second connection starves the pool.
    public CheckResult check(UUID userId, String key, long consume, Long currentCount) {
        return check(userId, key, consume, currentCount, Instant.now());
    }

    public CheckResult check(UUID userId, String key, long consume, Long currentCount, Instant now) {
        String tier = effectiveTier(userId);
        List<PlanEntitlement> all = entitlements.findByKey(key);
        if (all.isEmpty()) throw new PjException(HttpStatus.NOT_FOUND, "UNKNOWN_ENTITLEMENT", "Unknown entitlement: " + key);
        PlanEntitlement e = all.stream().filter(x -> x.getTier().equals(tier)).findFirst()
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "UNKNOWN_ENTITLEMENT", "Unknown entitlement: " + key));

        if (!e.isEnabled()) return new CheckResult(false, null, "NOT_INCLUDED", upgradeFor(all, tier, e));
        if (e.getKind() == Kind.FLAG || e.getQuotaLimit() == null) return new CheckResult(true, null, null, null);

        long limit = e.getQuotaLimit();
        if (e.getKind() == Kind.CAP) {
            long cur = currentCount == null ? 0 : currentCount;
            boolean ok = cur < limit;
            return new CheckResult(ok, Math.max(0, limit - cur), ok ? null : "QUOTA_EXHAUSTED", ok ? null : upgradeFor(all, tier, e));
        }

        String pk = periodKey(e.getPeriodUnit(), now);
        String id = counterId(userId, key, pk);
        if (consume <= 0) {                                   // pure peek
            long used = counters.findById(id).map(UsageCounter::getUsed).orElse(0L);
            boolean ok = used < limit;
            return new CheckResult(ok, Math.max(0, limit - used), ok ? null : "QUOTA_EXHAUSTED", ok ? null : upgradeFor(all, tier, e));
        }
        try {
            counterRows.create(id, userId, key, pk);
        } catch (DataIntegrityViolationException raced) { /* created concurrently */ }
        // Striped in-process lock around the (self-committing) locked consume. The DB row lock is what protects
        // across instances on PostgreSQL; this extra guard makes the guarantee hold on H2, whose FOR UPDATE does
        // not reliably serialise concurrent writers. TODO: verify on PostgreSQL via Testcontainers in CI.
        var stripe = STRIPES[Math.floorMod(id.hashCode(), STRIPES.length)];
        stripe.lock();
        CounterRows.Consumed r;
        try {
            r = counterRows.consume(id, consume, limit);
        } finally {
            stripe.unlock();
        }
        long used = r.used();
        if (r.allowed()) return new CheckResult(true, Math.max(0, limit - used), null, null);
        return new CheckResult(false, Math.max(0, limit - used), "QUOTA_EXHAUSTED", upgradeFor(all, tier, e));
    }

    /** Cheapest higher tier that enables the key with a larger (or unlimited) allowance. */
    private String upgradeFor(List<PlanEntitlement> all, String currentTier, PlanEntitlement current) {
        Map<String, Integer> rank = new HashMap<>();
        plans.findAll().forEach(p -> rank.put(p.getCode(), p.getTierRank()));
        int cur = rank.getOrDefault(currentTier, 0);
        return all.stream()
                .filter(x -> rank.getOrDefault(x.getTier(), 0) > cur && x.isEnabled())
                .filter(x -> x.getQuotaLimit() == null || current.getQuotaLimit() == null || x.getQuotaLimit() > current.getQuotaLimit())
                .min(Comparator.comparingInt(x -> rank.getOrDefault(x.getTier(), 0)))
                .map(PlanEntitlement::getTier).orElse(null);
    }

    // ================= helpers =================
    static String periodKey(Period p, Instant now) {
        return switch (p) { case DAY -> DAY.format(now); case MONTH -> MONTH.format(now); case NONE -> "ALL"; };
    }

    static String counterId(UUID userId, String key, String periodKey) { return userId + ":" + key + ":" + periodKey; }

    static Instant resetsAt(Period p, Instant now) {
        LocalDate d = now.atZone(ZoneOffset.UTC).toLocalDate();
        return switch (p) {
            case DAY -> d.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            case MONTH -> d.withDayOfMonth(1).plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            case NONE -> null;
        };
    }

    /** Map.of rejects nulls; event payloads sometimes carry optional fields. */
    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) if (kv[i + 1] != null) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
