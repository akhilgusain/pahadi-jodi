package in.pahadijodi.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Stripe adapter for the diaspora (USD etc.). STUB-GRADE: PaymentIntent create/refund and webhook verification
 * follow the public API, reconciliation is not implemented yet (returns empty). Needs STRIPE_SECRET_KEY / STRIPE_WEBHOOK_SECRET.
 */
public class StripeGateway implements PaymentGateway {
    private static final Duration TOLERANCE = Duration.ofMinutes(5);
    private final RestClient http;
    private final String webhookSecret;
    private final ObjectMapper mapper;

    public StripeGateway(String baseUrl, String secretKey, String webhookSecret, ObjectMapper mapper) {
        this.webhookSecret = webhookSecret; this.mapper = mapper;
        this.http = RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Bearer " + secretKey).build();
    }

    @Override public String provider() { return "STRIPE"; }

    @Override
    public ProviderOrder createOrder(CreateOrder r) {
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("amount", Long.toString(r.amountMinor()));
            form.add("currency", r.currency().toLowerCase());
            form.add("metadata[orderId]", r.orderId().toString());
            form.add("automatic_payment_methods[enabled]", "true");
            JsonNode res = http.post().uri("/v1/payment_intents").header("Idempotency-Key", r.orderId().toString())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
            return new ProviderOrder(res.path("id").asText(), Map.of("provider", "STRIPE",
                    "clientSecret", res.path("client_secret").asText(), "amount", r.amountMinor(), "currency", r.currency()));
        } catch (Exception e) {
            throw new PjException(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR", "Stripe PaymentIntent creation failed");
        }
    }

    /** Stripe-Signature: t=<unix>,v1=<hmac_sha256_hex of "t.body"> with a 5 minute tolerance. */
    @Override
    public boolean verifyWebhookSignature(String rawBody, String header) {
        if (header == null || webhookSecret == null || webhookSecret.isBlank()) return false;
        String t = null, v1 = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals("t")) t = kv[1];
            if (kv.length == 2 && kv[0].equals("v1")) v1 = kv[1];
        }
        if (t == null || v1 == null) return false;
        try {
            if (Duration.between(Instant.ofEpochSecond(Long.parseLong(t)), Instant.now()).abs().compareTo(TOLERANCE) > 0) return false;
        } catch (NumberFormatException e) { return false; }
        return Hmac.safeEquals(Hmac.sha256Hex(webhookSecret, t + "." + rawBody), v1);
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody, String eventIdHeader) {
        try {
            JsonNode n = mapper.readTree(rawBody);
            JsonNode obj = n.path("data").path("object");
            EventType type = switch (n.path("type").asText()) {
                case "payment_intent.succeeded" -> EventType.PAYMENT_CAPTURED;
                case "payment_intent.payment_failed" -> EventType.PAYMENT_FAILED;
                case "payment_intent.amount_capturable_updated" -> EventType.PAYMENT_AUTHORIZED;
                case "charge.refunded" -> EventType.REFUND_PROCESSED;
                default -> EventType.OTHER;
            };
            return new WebhookEvent(n.path("id").asText(), type, obj.path("id").asText(null), obj.path("latest_charge").asText(obj.path("id").asText(null)),
                    obj.path("amount").asLong(0), obj.path("currency").asText("").toUpperCase(), "CARD",
                    obj.path("last_payment_error").path("code").asText(null));
        } catch (Exception e) {
            throw new PjException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Unparseable webhook body");
        }
    }

    @Override
    public String refund(String providerPaymentId, long amountMinor, String idempotencyKey) {
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("payment_intent", providerPaymentId);
            form.add("amount", Long.toString(amountMinor));
            JsonNode res = http.post().uri("/v1/refunds").header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
            return res.path("id").asText();
        } catch (Exception e) {
            throw new PjException(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR", "Stripe refund failed");
        }
    }

    @Override
    public List<ProviderPayment> fetchPayments(LocalDate date) {
        return List.of();   // TODO: balance transactions listing for reconciliation
    }
}
