package in.pahadijodi.payment.service;

import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.payment.domain.*;
import in.pahadijodi.payment.gateway.GatewayRegistry;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundService {
    private final PaymentOrderRepository orders;
    private final RefundRepository refunds;
    private final GatewayRegistry gateways;
    private final InvoiceService invoices;
    private final OutboxService outbox;

    public RefundService(PaymentOrderRepository orders, RefundRepository refunds, GatewayRegistry gateways,
                         InvoiceService invoices, OutboxService outbox) {
        this.orders = orders; this.refunds = refunds; this.gateways = gateways; this.invoices = invoices; this.outbox = outbox;
    }

    /** @param amountMinor null = refund everything still refundable */
    @Transactional
    public Refund refund(UUID orderId, Long amountMinor, String reason, String requestedBy) {
        PaymentOrder o = orders.findById(orderId).orElseThrow(() ->
                new PjException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
        if (o.getStatus() != OrderStatus.CAPTURED && o.getStatus() != OrderStatus.PARTIALLY_REFUNDED) {
            throw new PjException(HttpStatus.CONFLICT, "ORDER_NOT_REFUNDABLE", "Order is " + o.getStatus());
        }
        long amount = amountMinor == null ? o.refundable() : amountMinor;
        if (amount <= 0 || amount > o.refundable()) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "REFUND_EXCEEDS_PAID",
                    "Refundable amount is " + o.refundable() + " " + o.getCurrency());
        }
        Refund r = new Refund(orderId, amount, reason == null ? "USER_REQUEST" : reason, requestedBy);
        r.processed(gateways.get(o.getProvider()).refund(o.getProviderPaymentId(), amount, r.getId().toString()));
        refunds.save(r);
        o.applyRefund(amount);
        orders.save(o);
        invoices.issueCreditNote(o, amount);
        outbox.publish("pj.payment.refunded.v1", "PaymentRefunded", o.getUserId().toString(), Map.of(
                "paymentId", o.getId().toString(), "userId", o.getUserId().toString(), "refundId", r.getId().toString(),
                "amountPaise", amount, "currency", o.getCurrency(), "full", o.getStatus() == OrderStatus.REFUNDED));
        return r;
    }
}
