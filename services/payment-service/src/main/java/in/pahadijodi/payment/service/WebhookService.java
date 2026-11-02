package in.pahadijodi.payment.service;

import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.payment.domain.*;
import in.pahadijodi.payment.gateway.GatewayRegistry;
import in.pahadijodi.payment.gateway.PaymentGateway;
import in.pahadijodi.payment.gateway.PaymentGateway.EventType;
import in.pahadijodi.payment.gateway.PaymentGateway.WebhookEvent;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    public enum Result { APPLIED, IGNORED, DUPLICATE, PARKED, AMOUNT_MISMATCH }

    private final GatewayRegistry gateways;
    private final PaymentOrderRepository orders;
    private final WebhookEventRepository events;
    private final InvoiceService invoices;
    private final OutboxService outbox;
    private final TransactionTemplate tx;

    public WebhookService(GatewayRegistry gateways, PaymentOrderRepository orders, WebhookEventRepository events,
                          InvoiceService invoices, OutboxService outbox, PlatformTransactionManager tm) {
        this.gateways = gateways; this.orders = orders; this.events = events; this.invoices = invoices;
        this.outbox = outbox; this.tx = new TransactionTemplate(tm);
    }

    /**
     * 1) verify the HMAC over the RAW body (never a re-serialised one), 2) record the event id (PK) so replays are no-ops,
     * 3) apply through the order state machine after checking amount/currency against OUR order, never trusting the payload alone.
     */
    public Result handle(String provider, String rawBody, String signature, String eventIdHeader) {
        PaymentGateway gw = gateways.get(provider);
        if (!gw.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Rejected {} webhook: bad signature", provider);
            throw new PjException(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Webhook signature verification failed");
        }
        WebhookEvent ev = gw.parseWebhook(rawBody, eventIdHeader);
        try {
            return tx.execute(status -> {
                if (events.existsById(provider.toUpperCase() + ":" + ev.eventId())) return Result.DUPLICATE;
                String[] error = new String[1];
                Result r = apply(ev, error);
                events.saveAndFlush(new in.pahadijodi.payment.domain.WebhookEvent(provider.toUpperCase(), ev.eventId(),
                        ev.type().name(), r.name(), error[0], rawBody));
                return r;
            });
        } catch (DataIntegrityViolationException dup) {
            return Result.DUPLICATE;    // concurrent delivery of the same event id lost the PK race
        }
    }

    private Result apply(WebhookEvent ev, String[] error) {
        if (ev.type() == EventType.OTHER || ev.type() == EventType.REFUND_PROCESSED) return Result.IGNORED;   // refunds are applied synchronously
        PaymentOrder order = ev.providerOrderId() == null ? null : orders.findByProviderOrderId(ev.providerOrderId()).orElse(null);
        if (order == null) {
            error[0] = "UNKNOWN_ORDER " + ev.providerOrderId();
            log.warn("Webhook for unknown order {}; parked for reconciliation", ev.providerOrderId());
            return Result.PARKED;
        }
        if (ev.type() == EventType.PAYMENT_CAPTURED
                && (ev.amountMinor() != order.getTotalMinor() || !order.getCurrency().equalsIgnoreCase(ev.currency()))) {
            error[0] = "AMOUNT_MISMATCH expected " + order.getTotalMinor() + " " + order.getCurrency()
                    + " got " + ev.amountMinor() + " " + ev.currency();
            log.error("Order {}: {}", order.getId(), error[0]);
            return Result.AMOUNT_MISMATCH;
        }
        OrderStatus target = switch (ev.type()) {
            case PAYMENT_AUTHORIZED -> OrderStatus.AUTHORIZED;
            case PAYMENT_CAPTURED -> OrderStatus.CAPTURED;
            default -> OrderStatus.FAILED;
        };
        if (!order.transitionTo(target)) return Result.IGNORED;   // out-of-order or duplicate semantic event

        if (target == OrderStatus.CAPTURED) {
            order.markCaptured(ev.providerPaymentId(), ev.method());
            orders.save(order);
            invoices.issueTaxInvoice(order);
            outbox.publish("pj.payment.captured.v1", "PaymentCaptured", order.getUserId().toString(), Map.of(
                    "paymentId", order.getId().toString(), "userId", order.getUserId().toString(),
                    "planCode", order.getPlanCode(), "period", order.getBillingPeriod(),
                    "amountPaise", order.getTotalMinor(), "currency", order.getCurrency()));
        } else if (target == OrderStatus.FAILED) {
            order.markFailed(ev.failureCode());
            orders.save(order);
            outbox.publish("pj.payment.failed.v1", "PaymentFailed", order.getUserId().toString(), Map.of(
                    "paymentId", order.getId().toString(), "userId", order.getUserId().toString(),
                    "planCode", order.getPlanCode(), "failureCode", String.valueOf(ev.failureCode()), "retryable", true));
        } else {
            orders.save(order);
        }
        return Result.APPLIED;
    }
}
