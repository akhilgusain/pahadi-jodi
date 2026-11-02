package in.pahadijodi.notification.service;

import java.util.UUID;

/** Published inside the processing transaction; the SSE hub pushes it AFTER commit. */
public record NotificationCreated(UUID userId, NotificationDto notification) {}
