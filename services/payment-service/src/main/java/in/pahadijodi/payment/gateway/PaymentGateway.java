package in.pahadijodi.payment.gateway;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Anti-corruption port around payment providers. Adapters: Fake (default), Razorpay (INR), Stripe (other currencies). */
public interface PaymentGateway {

    /** "RAZORPAY" or "STRIPE". */
    String provider();

    ProviderOrder createOrder(CreateOrder request);

    /** Constant-time HMAC check of the raw request body. */
    boolean verifyWebhookSignature(String rawBody, String signatureHeader);

    /** @param eventIdHeader provider event id header if the provider sends one (may be null) */
    WebhookEvent parseWebhook(String rawBody, String eventIdHeader);

    /** @return provider refund id. {@code idempotencyKey} must make retries safe. */
    String refund(String providerPaymentId, long amountMinor, String idempotencyKey);

    /** Provider-side view of captured payments for a business date (reconciliation). */
    List<ProviderPayment> fetchPayments(LocalDate date);

    record CreateOrder(UUID orderId, long amountMinor, String currency, String receipt) {}
    record ProviderOrder(String providerOrderId, Map<String, Object> clientParams) {}
    record ProviderPayment(String providerPaymentId, String providerOrderId, long amountMinor, String currency, String status) {}

    enum EventType { PAYMENT_AUTHORIZED, PAYMENT_CAPTURED, PAYMENT_FAILED, REFUND_PROCESSED, OTHER }

    record WebhookEvent(String eventId, EventType type, String providerOrderId, String providerPaymentId,
                        long amountMinor, String currency, String method, String failureCode) {}
}
