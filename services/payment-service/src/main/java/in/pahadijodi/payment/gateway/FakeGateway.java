package in.pahadijodi.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;

/**
 * Local stand-in for a provider. Creates orders instantly, verifies HMAC webhooks with a local secret,
 * and remembers "captured" payments so reconciliation has a provider-side truth to compare against.
 * One instance per provider name so INR (RAZORPAY) and non-INR (STRIPE) both work without keys.
 */
public class FakeGateway implements PaymentGateway {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final String provider;
    private final String secret;
    private final ObjectMapper mapper;
    private final Map<String, ProviderPayment> payments = new ConcurrentHashMap<>();
    private final Map<String, LocalDate> paymentDates = new ConcurrentHashMap<>();
    private final Map<String, String> refunds = new ConcurrentHashMap<>();   // idempotencyKey -> refundId

    public FakeGateway(String provider, String secret, ObjectMapper mapper) {
        this.provider = provider; this.secret = secret; this.mapper = mapper;
    }

    @Override public String provider() { return provider; }

    @Override
    public ProviderOrder createOrder(CreateOrder r) {
        String id = "order_fake_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        return new ProviderOrder(id, Map.of("provider", provider, "mode", "fake", "orderId", id,
                "amount", r.amountMinor(), "currency", r.currency()));
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return Hmac.safeEquals(Hmac.sha256Hex(secret, rawBody), signature);
    }

    public String sign(String rawBody) { return Hmac.sha256Hex(secret, rawBody); }

    @Override
    public WebhookEvent parseWebhook(String rawBody, String eventIdHeader) {
        try {
            JsonNode n = mapper.readTree(rawBody);
            String type = n.path("type").asText("");
            EventType et = switch (type) {
                case "payment.authorized" -> EventType.PAYMENT_AUTHORIZED;
                case "payment.captured" -> EventType.PAYMENT_CAPTURED;
                case "payment.failed" -> EventType.PAYMENT_FAILED;
                case "refund.processed" -> EventType.REFUND_PROCESSED;
                default -> EventType.OTHER;
            };
            String eventId = n.path("id").asText(eventIdHeader);
            if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("missing event id");
            return new WebhookEvent(eventId, et, n.path("orderId").asText(null), n.path("paymentId").asText(null),
                    n.path("amount").asLong(0), n.path("currency").asText(null), n.path("method").asText(null),
                    n.path("failureCode").asText(null));
        } catch (Exception e) {
            throw new PjException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Unparseable webhook body");
        }
    }

    @Override
    public String refund(String providerPaymentId, long amountMinor, String idempotencyKey) {
        if (!payments.containsKey(providerPaymentId)) {
            throw new PjException(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR", "Unknown provider payment " + providerPaymentId);
        }
        return refunds.computeIfAbsent(idempotencyKey, k -> "rfnd_fake_" + UUID.randomUUID().toString().substring(0, 12));
    }

    @Override
    public List<ProviderPayment> fetchPayments(LocalDate date) {
        return payments.values().stream().filter(p -> date.equals(paymentDates.get(p.providerPaymentId()))).toList();
    }

    // ---- dev/test helpers ----

    /** Builds a signed webhook as the provider would send it, and records the payment on the "provider side". */
    public SignedWebhook simulate(String type, String providerOrderId, long amountMinor, String currency, String failureCode) {
        String paymentId = "pay_fake_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        if ("payment.captured".equals(type)) {
            injectProviderPayment(new ProviderPayment(paymentId, providerOrderId, amountMinor, currency, "captured"));
        }
        return signedWebhook("evt_" + UUID.randomUUID().toString().substring(0, 12), type, providerOrderId, paymentId,
                amountMinor, currency, failureCode);
    }

    public SignedWebhook signedWebhook(String eventId, String type, String providerOrderId, String paymentId,
                                       long amountMinor, String currency, String failureCode) {
        try {
            var node = mapper.createObjectNode();
            node.put("id", eventId).put("type", type).put("orderId", providerOrderId).put("paymentId", paymentId)
                    .put("amount", amountMinor).put("currency", currency).put("method", "UPI");
            if (failureCode != null) node.put("failureCode", failureCode);
            String body = mapper.writeValueAsString(node);
            return new SignedWebhook(body, sign(body), eventId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void injectProviderPayment(ProviderPayment p) {
        payments.put(p.providerPaymentId(), p);
        paymentDates.put(p.providerPaymentId(), LocalDate.now(IST));
    }

    public record SignedWebhook(String body, String signature, String eventId) {}
}
