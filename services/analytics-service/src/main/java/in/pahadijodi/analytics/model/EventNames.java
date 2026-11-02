package in.pahadijodi.analytics.model;

import java.util.List;

/** Canonical event names, independent of the (still evolving) Kafka topic naming. */
public final class EventNames {
    private EventNames() {}

    public static final String REGISTERED = "REGISTERED";
    public static final String PROFILE_UPDATED = "PROFILE_UPDATED";
    public static final String PROFILE_COMPLETE = "PROFILE_COMPLETE";   // profile saved with completeness >= 80
    public static final String PROFILE_VIEWED = "PROFILE_VIEWED";
    public static final String INTEREST_SENT = "INTEREST_SENT";
    public static final String INTEREST_ACCEPTED = "INTEREST_ACCEPTED";
    public static final String MATCH_CREATED = "MATCH_CREATED";
    public static final String MESSAGE_SENT = "MESSAGE_SENT";
    public static final String SUBSCRIPTION_ACTIVATED = "SUBSCRIPTION_ACTIVATED";
    public static final String PAYMENT_CAPTURED = "PAYMENT_CAPTURED";
    public static final String OTHER = "OTHER";

    public static final String ACTIVE_USERS = "ACTIVE_USERS";
    public static final String REVENUE_PREFIX = "REVENUE_";             // REVENUE_INR, REVENUE_USD (minor units)
    public static final String NO_ACTOR = "-";

    /** Funnel order (strict: a user counts at a stage only if they also passed every earlier stage). */
    public static final List<String> FUNNEL = List.of(REGISTERED, PROFILE_COMPLETE, INTEREST_SENT, MATCH_CREATED, MESSAGE_SENT, PAYMENT_CAPTURED);
}
