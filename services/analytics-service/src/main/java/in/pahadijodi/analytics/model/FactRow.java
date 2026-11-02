package in.pahadijodi.analytics.model;

import java.time.Instant;
import java.time.LocalDate;

public record FactRow(String eventId, String eventName, String topic, String userHash, String targetHash,
                      Instant occurredAt, LocalDate day, Long amountMinor, String currency, String plan,
                      String community, String district) {}
