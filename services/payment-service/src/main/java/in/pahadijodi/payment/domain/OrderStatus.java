package in.pahadijodi.payment.domain;

import java.util.EnumSet;
import java.util.Set;

/** Monotonic order state machine; out-of-order provider events that would go "backwards" are ignored. */
public enum OrderStatus {
    CREATED, AUTHORIZED, CAPTURED, FAILED, PARTIALLY_REFUNDED, REFUNDED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case CREATED -> EnumSet.of(AUTHORIZED, CAPTURED, FAILED);
            case AUTHORIZED -> EnumSet.of(CAPTURED, FAILED);
            case FAILED -> EnumSet.of(AUTHORIZED, CAPTURED);        // customer retried on the same provider order
            case CAPTURED -> EnumSet.of(PARTIALLY_REFUNDED, REFUNDED);
            case PARTIALLY_REFUNDED -> EnumSet.of(PARTIALLY_REFUNDED, REFUNDED);
            case REFUNDED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target) { return next().contains(target); }
}
