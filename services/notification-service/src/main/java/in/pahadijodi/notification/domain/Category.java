package in.pahadijodi.notification.domain;

/**
 * TRANSACTIONAL (account, payment, security): ignores quiet hours and daily caps, and email cannot be opted out.
 * ENGAGEMENT (interest, match, message): respects quiet hours (deferred, not dropped) and daily caps.
 */
public enum Category { TRANSACTIONAL, ENGAGEMENT }
