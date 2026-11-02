package in.pahadijodi.analytics.api;

import in.pahadijodi.analytics.api.AnalyticsDtos.DevEvent;
import in.pahadijodi.analytics.api.AnalyticsDtos.DevIngestRequest;
import in.pahadijodi.analytics.service.IngestService;
import in.pahadijodi.analytics.service.IngestService.Result;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feeds events without Kafka. Exists only when pj.analytics.dev-ingest.enabled=true (the h2 profile). The bean is not
 * created otherwise, so the route is a 404 in production, and it still requires an ADMIN token.
 */
@RestController
@RequestMapping("/api/v1/analytics/dev")
@ConditionalOnProperty(name = "pj.analytics.dev-ingest.enabled", havingValue = "true")
public class DevIngestController {
    private final IngestService ingest;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public DevIngestController(IngestService ingest, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.ingest = ingest;
        this.json = json;
    }

    @PostMapping("/ingest")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, Object> ingest(@Valid @RequestBody DevIngestRequest req) {
        int stored = 0, dup = 0, ignored = 0;
        for (DevEvent e : req.events()) {
            var envelope = json.createObjectNode();
            if (e.eventId() != null) envelope.put("eventId", e.eventId());
            if (e.occurredAt() != null) envelope.put("occurredAt", e.occurredAt());
            envelope.set("payload", e.payload());
            Result r = ingest.ingest(e.topic(), envelope);
            stored += r.stored();
            dup += r.duplicates();
            ignored += r.ignored();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("received", req.events().size());
        out.put("rowsStored", stored);
        out.put("duplicates", dup);
        out.put("ignored", ignored);
        return out;
    }
}
