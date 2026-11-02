package in.pahadijodi.notification.domain;

/** PENDING (also used for retry and quiet-hours deferral) -> SENT | DEAD; SUPPRESSED = deliberately not sent. */
public enum DeliveryStatus { PENDING, SENT, SUPPRESSED, DEAD }
