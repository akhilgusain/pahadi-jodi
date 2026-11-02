package in.pahadijodi.subscription.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One current subscription row per user.
 * <pre>
 *  PENDING -> ACTIVE -> GRACE -> EXPIRED
 *               |  ^      |
 *               |  +------+   (renewal payment)        EXPIRED/CANCELED -> ACTIVE (re-purchase)
 *               +-> EXPIRED  (cancel_at_period_end)     any live state   -> CANCELED (refund/admin)
 * </pre>
 */
@Entity
@Table(name = "subscription")
public class Subscription {
    public enum Status { PENDING, ACTIVE, GRACE, EXPIRED, CANCELED }

    private static final Map<Status, Set<Status>> ALLOWED = Map.of(
            Status.PENDING, EnumSet.of(Status.ACTIVE, Status.CANCELED),
            Status.ACTIVE, EnumSet.of(Status.ACTIVE, Status.GRACE, Status.EXPIRED, Status.CANCELED),
            Status.GRACE, EnumSet.of(Status.ACTIVE, Status.EXPIRED, Status.CANCELED),
            Status.EXPIRED, EnumSet.of(Status.ACTIVE),
            Status.CANCELED, EnumSet.of(Status.ACTIVE));

    @Id private UUID id;
    @Column(name = "user_id", nullable = false, unique = true) private UUID userId;
    @Column(name = "plan_code", nullable = false) private String planCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "current_period_end", nullable = false) private Instant currentPeriodEnd;
    @Column(name = "grace_ends_at") private Instant graceEndsAt;
    @Column(name = "cancel_at_period_end", nullable = false) private boolean cancelAtPeriodEnd;
    @Column(name = "last_payment_id") private String lastPaymentId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Subscription() {}

    public Subscription(UUID userId, String planCode, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.planCode = planCode;
        this.status = Status.PENDING;
        this.startedAt = now;
        this.currentPeriodEnd = now;
        this.createdAt = this.updatedAt = now;
    }

    public UUID getUserId() { return userId; }
    public String getPlanCode() { return planCode; }
    public Status getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public Instant getGraceEndsAt() { return graceEndsAt; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public String getLastPaymentId() { return lastPaymentId; }

    /** ACTIVE and GRACE keep their entitlements (grace = payment retry window). */
    public boolean grantsEntitlements() { return status == Status.ACTIVE || status == Status.GRACE; }

    public void activate(String planCode, Instant start, Instant periodEnd, String paymentId) {
        move(Status.ACTIVE);
        this.planCode = planCode;
        this.startedAt = start;
        this.currentPeriodEnd = periodEnd;
        this.graceEndsAt = null;
        this.cancelAtPeriodEnd = false;
        this.lastPaymentId = paymentId;
    }
    public void enterGrace(Instant graceEnds) { move(Status.GRACE); this.graceEndsAt = graceEnds; }
    public void expire() { move(Status.EXPIRED); this.graceEndsAt = null; }
    public void cancelNow() { move(Status.CANCELED); this.graceEndsAt = null; }
    public void setCancelAtPeriodEnd(boolean v) {
        if (!grantsEntitlements()) throw new IllegalStateException("Subscription is " + status);
        this.cancelAtPeriodEnd = v; this.updatedAt = Instant.now();
    }

    private void move(Status to) {
        if (!ALLOWED.get(status).contains(to)) throw new IllegalStateException(status + " -> " + to + " is not allowed");
        this.status = to;
        this.updatedAt = Instant.now();
    }
}
