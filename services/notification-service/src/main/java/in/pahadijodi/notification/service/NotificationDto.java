package in.pahadijodi.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.notification.domain.Notification;
import in.pahadijodi.notification.domain.NotificationType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationDto(UUID id, NotificationType type, String title, String body, Map<String, String> data,
                              Instant createdAt, Instant readAt, boolean read) {
    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() {};

    public static NotificationDto from(Notification n, ObjectMapper mapper) {
        Map<String, String> data = Map.of();
        try {
            if (n.getDataJson() != null) data = mapper.readValue(n.getDataJson(), MAP);
        } catch (Exception ignored) { /* corrupt data_json must not break the inbox */ }
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(), data, n.getCreatedAt(), n.getReadAt(),
                n.getReadAt() != null);
    }
}
