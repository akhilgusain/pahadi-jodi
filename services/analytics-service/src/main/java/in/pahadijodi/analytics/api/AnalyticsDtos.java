package in.pahadijodi.analytics.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    /** One synthetic event for the dev endpoint; same shape as a Kafka envelope. */
    public record DevEvent(@NotBlank String topic, String eventId, String occurredAt, @NotNull JsonNode payload) {}

    public record DevIngestRequest(@NotNull @Size(min = 1, max = 1000) List<DevEvent> events) {}
}
