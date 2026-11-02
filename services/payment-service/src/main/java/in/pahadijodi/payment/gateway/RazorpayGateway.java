package in.pahadijodi.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Real Razorpay adapter (INR). Needs RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET / RAZORPAY_WEBHOOK_SECRET.
 * Written against the public REST docs; NOT exercised against the live API in this repo's tests.
 */
public class RazorpayGateway implements PaymentGateway {
    private final RestClient http;
    private final String keyId;
    private final String webhookSecret;
    private final ObjectMapper mapper;

    public RazorpayGateway(String baseUrl, String keyId, String keySecret, String webhookSecret, ObjectMapper mapper) {
        this.keyId = keyId; this.webhookSecret = webhookSecret; this.mapper = mapper;
        String basic = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        this.http = RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Basic " + basic).build();
    }

    @Override public String provider() { return "RAZORPAY"; }

    @Override
    public ProviderOrder createOrder(CreateOrder r) {
        try {
            JsonNode res = http.post().uri("/v1/orders").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", r.amountMinor(), "currency", r.currency(), "receipt", r.receipt(),
                            "notes", Map.of("orderId", r.orderId().toString())))
                    .retrieve().body(JsonNode.class);
            String id = res.path("id").asText();
            return new ProviderOrder(id, Map.of("provider", "RAZORPAY", "keyId", keyId, "orderId", id,
                    "amount", r.amountMinor(), "currency", r.currency()));
        } catch (Exception e) {
            throw new PjException(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR", "Razorpay order creation failed");
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return Hmac.safeEquals(Hmac.sha256Hex(webhookSecret == null ? "" : webhookSecret, rawBody), signature)
                && webhookSecret != null && !webhookSecret.isBlank();
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody, String eventIdHeader) {
        try {
            JsonNode n = mapper.readTree(rawBody);
            String ev = n.path("event").asText();
            JsonNode pay = n.path("payload").path("payment").path("entity");
            EventType type = switch (ev) {
                case "payment.authorized" -> EventType.PAYMENT_AUTHORIZED;
                case "payment.captured", "order.paid" -> EventType.PAYMENT_CAPTURED;
                case "payment.failed" -> EventType.PAYMENT_FAILED;
                case "refund.processed" -> EventType.REFUND_PROCESSED;
                default -> EventType.OTHER;
            };
            String eventId = eventIdHeader != null && !eventIdHeader.isBlank() ? eventIdHeader
                    : "sha-" + Hmac.sha256Hex("event-id", rawBody);   // fallback: content hash
            return new WebhookEvent(eventId, type, pay.path("order_id").asText(null), pay.path("id").asText(null),
                    pay.path("amount").asLong(0), pay.path("currency").asText(null), pay.path("method").asText(null),
                    pay.path("error_code").asText(null));
        } catch (Exception e) {
            throw new PjException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Unparseable webhook body");
        }
    }

    @Override
    public String refund(String providerPaymentId, long amountMinor, String idempotencyKey) {
        try {
            JsonNode res = http.post().uri("/v1/payments/{id}/refund", providerPaymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", amountMinor, "notes", Map.of("idempotencyKey", idempotencyKey)))
                    .retrieve().body(JsonNode.class);
            return res.path("id").asText();
        } catch (Exception e) {
            throw new PjException(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR", "Razorpay refund failed");
        }
    }

    @Override
    public List<ProviderPayment> fetchPayments(LocalDate date) {
        long from = date.atStartOfDay(FakeGateway.IST).toEpochSecond();
        long to = date.plusDays(1).atStartOfDay(FakeGateway.IST).toEpochSecond() - 1;
        List<ProviderPayment> out = new ArrayList<>();
        try {
            JsonNode res = http.get().uri("/v1/payments?from={f}&to={t}&count=100", from, to).retrieve().body(JsonNode.class);
            for (JsonNode p : res.path("items")) {
                if ("captured".equals(p.path("status").asText())) {
                    out.add(new ProviderPayment(p.path("id").asText(), p.path("order_id").asText(null),
                            p.path("amount").asLong(), p.path("currency").asText(), "captured"));
                }
            }   // TODO: paginate with skip= when > 100 payments/day
            return out;
        } catch (Exception e) {
            throw new PjException(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR", "Razorpay fetch failed at " + Instant.now());
        }
    }
}
