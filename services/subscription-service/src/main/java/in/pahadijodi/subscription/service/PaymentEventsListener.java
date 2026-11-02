package in.pahadijodi.subscription.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Payment saga participant: payment captured -> activate/renew; payment refunded -> cancel immediately. */
@Component
public class PaymentEventsListener {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);
    private static final Set<String> SUBSCRIPTION_PURPOSES = Set.of("SUBSCRIPTION_NEW", "SUBSCRIPTION_RENEWAL", "UPGRADE");

    private final SubscriptionService service;
    private final ObjectMapper mapper;

    public PaymentEventsListener(SubscriptionService service, ObjectMapper mapper) {
        this.service = service; this.mapper = mapper;
    }

    // Both spellings exist in the design docs (docs/03 vs docs/05); accept either.
    @KafkaListener(topics = {"pj.payment.captured.v1", "pj.payment.payment.captured.v1"},
            autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void onCaptured(String message) { handleCaptured(message); }

    @KafkaListener(topics = {"pj.payment.refunded.v1", "pj.payment.payment.refunded.v1"},
            autoStartup = "${pj.kafka.consumers.enabled:true}")
    public void onRefunded(String message) { handleRefunded(message); }

    /** Never throws: a poison message must not block the partition (it is logged and skipped). */
    public void handleCaptured(String message) {
        try {
            JsonNode p = mapper.readTree(message).path("payload");
            String purpose = p.path("purpose").asText("");
            if (!purpose.isEmpty() && !SUBSCRIPTION_PURPOSES.contains(purpose)) return;   // e.g. BOOST
            String plan = p.path("planCode").asText("");
            if (plan.isEmpty() || !p.path("userId").isTextual()) { log.warn("captured event without userId/planCode; ignored"); return; }
            String paymentId = p.path("paymentId").asText(p.path("orderId").asText(null));
            service.activate(UUID.fromString(p.path("userId").asText()), plan, p.path("billingCycle").asText(null), paymentId);
        } catch (Exception e) {
            log.warn("Ignoring payment.captured event: {}", e.toString());
        }
    }

    public void handleRefunded(String message) {
        try {
            JsonNode p = mapper.readTree(message).path("payload");
            if (p.path("userId").isTextual()) service.cancelNow(UUID.fromString(p.path("userId").asText()), "REFUND");
        } catch (Exception e) {
            log.warn("Ignoring payment.refunded event: {}", e.toString());
        }
    }
}
