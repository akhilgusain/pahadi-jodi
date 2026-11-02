package in.pahadijodi.subscription.service;

import in.pahadijodi.subscription.domain.Repos.UsageCounters;
import in.pahadijodi.subscription.domain.UsageCounter;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a counter row in its OWN transaction (called through the proxy from another bean), so losing the
 * insert race (DataIntegrityViolationException, caught by the caller) does not poison the caller's tx.
 */
@Component
public class CounterRows {
    private final UsageCounters counters;

    public CounterRows(UsageCounters counters) { this.counters = counters; }

    public record Consumed(boolean allowed, long used) {}

    /** Locks the counter row (FOR UPDATE), checks and increments, in one short transaction of its own. */
    @Transactional
    public Consumed consume(String id, long n, long limit) {
        UsageCounter row = counters.lockById(id).orElseThrow();
        boolean ok = row.tryConsume(n, limit);
        return new Consumed(ok, row.getUsed());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(String id, UUID userId, String key, String periodKey) {
        if (!counters.existsById(id)) counters.saveAndFlush(new UsageCounter(id, userId, key, periodKey));
    }
}
