package in.pahadijodi.subscription.domain;

import java.time.Instant;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class Repos {
    private Repos() {}

    public interface Plans extends JpaRepository<Plan, String> {
        List<Plan> findByActiveTrueOrderByTierRank();
    }

    public interface Entitlements extends JpaRepository<PlanEntitlement, PlanEntitlement.Key> {
        List<PlanEntitlement> findByTier(String tier);
        @Query("select e from PlanEntitlement e where e.key = :key")
        List<PlanEntitlement> findByKey(@Param("key") String key);
    }

    public interface Subscriptions extends JpaRepository<Subscription, UUID> {
        Optional<Subscription> findByUserId(UUID userId);
        List<Subscription> findByStatusAndCurrentPeriodEndBefore(Subscription.Status s, Instant t);
        List<Subscription> findByStatusAndGraceEndsAtBefore(Subscription.Status s, Instant t);
    }

    public interface AppliedPayments extends JpaRepository<AppliedPayment, String> {}

    public interface UsageCounters extends JpaRepository<UsageCounter, String> {
        /**
         * Row lock (SELECT ... FOR UPDATE) so concurrent consumers serialise and each sees the latest committed
         * count. A single "UPDATE ... WHERE used + n <= limit" is only safe on databases that re-evaluate the
         * predicate after waiting for the lock (PostgreSQL does, H2 does not reliably), so we lock explicitly.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select u from UsageCounter u where u.id = :id")
        Optional<UsageCounter> lockById(@Param("id") String id);

        @Query("select u from UsageCounter u where u.userId = :userId and u.periodKey in :periods")
        List<UsageCounter> findForPeriods(@Param("userId") UUID userId, @Param("periods") List<String> periods);
    }
}
