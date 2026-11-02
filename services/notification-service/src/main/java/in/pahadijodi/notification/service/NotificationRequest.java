package in.pahadijodi.notification.service;

import in.pahadijodi.notification.domain.NotificationType;
import java.util.Map;
import java.util.UUID;

/**
 * A routed intent to notify one user. {@code dedupeKey} makes processing idempotent (Kafka redelivery, retries).
 * {@code params} fill template placeholders; {@code data} is client-visible metadata (deepLink, ids).
 */
public record NotificationRequest(UUID userId, NotificationType type, String dedupeKey,
                                  Map<String, String> params, Map<String, String> data) {}
