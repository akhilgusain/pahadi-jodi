package in.pahadijodi.payment.api;

import in.pahadijodi.payment.domain.Mandate;
import in.pahadijodi.payment.domain.PaymentOrder;
import in.pahadijodi.payment.service.PriceTable.Period;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record CreateOrderRequest(
            @NotBlank @Pattern(regexp = "PREMIUM|PREMIUM_PLUS|VIP") String planCode,
            @NotNull Period billingPeriod,
            @NotBlank @Pattern(regexp = "INR|USD") String currency,
            @Pattern(regexp = "\\d{2}", message = "two-digit GST state code, e.g. 05") String stateCode) {}

    public record RefundRequest(Long amountMinor, @Pattern(regexp = "USER_REQUEST|DUPLICATE|FRAUD|SERVICE_ISSUE|CHARGEBACK") String reason) {}

    public record MandateRequest(@NotBlank @Pattern(regexp = "PREMIUM|PREMIUM_PLUS|VIP") String planCode,
                                 @NotNull Period billingPeriod, @NotBlank @Pattern(regexp = "INR|USD") String currency, String method) {}

    public record OrderResponse(UUID orderId, String provider, String providerOrderId, String status, String planCode,
                                String billingPeriod, String currency, long amountMinor, long taxMinor, long totalMinor,
                                long refundedMinor, String failureCode, Map<String, Object> clientParams, Instant createdAt) {
        public static OrderResponse from(PaymentOrder o, Map<String, Object> clientParams) {
            return new OrderResponse(o.getId(), o.getProvider(), o.getProviderOrderId(), o.getStatus().name(), o.getPlanCode(),
                    o.getBillingPeriod(), o.getCurrency(), o.getAmountMinor(), o.getTaxMinor(), o.getTotalMinor(),
                    o.getRefundedMinor(), o.getFailureCode(), clientParams, o.getCreatedAt());
        }
    }

    public record MandateResponse(UUID id, String provider, String planCode, String billingPeriod, String method, String status,
                                  long maxAmountMinor, String currency, Instant nextChargeAt) {
        public static MandateResponse from(Mandate m) {
            return new MandateResponse(m.getId(), m.getProvider(), m.getPlanCode(), m.getBillingPeriod(), m.getMethod(),
                    m.getStatus(), m.getMaxAmountMinor(), m.getCurrency(), m.getNextChargeAt());
        }
    }
}
