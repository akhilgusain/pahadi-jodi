package in.pahadijodi.payment.api;

import static in.pahadijodi.common.security.ResourceServerAutoConfiguration.userId;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.payment.api.PaymentDtos.*;
import in.pahadijodi.payment.domain.*;
import in.pahadijodi.payment.service.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final OrderService orders;
    private final PaymentOrderRepository orderRepo;
    private final InvoiceService invoices;
    private final RefundService refunds;
    private final MandateService mandates;
    private final ReconciliationService reconciliation;

    public PaymentController(OrderService orders, PaymentOrderRepository orderRepo, InvoiceService invoices,
                             RefundService refunds, MandateService mandates, ReconciliationService reconciliation) {
        this.orders = orders; this.orderRepo = orderRepo; this.invoices = invoices; this.refunds = refunds;
        this.mandates = mandates; this.reconciliation = reconciliation;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse createOrder(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String key,
                              @Valid @RequestBody CreateOrderRequest r) {
        if (key.isBlank() || key.length() > 100) throw new PjException(HttpStatus.BAD_REQUEST, "BAD_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-100 chars");
        var created = orders.create(userId(jwt), key, new OrderService.CreateOrderCommand(r.planCode(), r.billingPeriod(), r.currency(), r.stateCode()));
        return OrderResponse.from(created.order(), created.clientParams());
    }

    @GetMapping
    List<OrderResponse> history(@AuthenticationPrincipal Jwt jwt) {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId(jwt)).stream().map(o -> OrderResponse.from(o, null)).toList();
    }

    @GetMapping("/{id}")
    OrderResponse get(@AuthenticationPrincipal Jwt jwt, Authentication auth, @PathVariable UUID id) {
        PaymentOrder o = owned(jwt, auth, id);
        return OrderResponse.from(o, orders.readParams(o.getClientParams()));
    }

    @GetMapping(value = "/{id}/invoice", produces = MediaType.TEXT_HTML_VALUE)
    String invoice(@AuthenticationPrincipal Jwt jwt, Authentication auth, @PathVariable UUID id) {
        PaymentOrder o = owned(jwt, auth, id);
        var list = invoices.forOrder(o.getId());
        if (list.isEmpty()) throw new PjException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "No invoice yet: payment not captured");
        return invoices.renderHtml(o, list);
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    java.util.Map<String, Object> refund(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody(required = false) RefundRequest r) {
        Refund refund = refunds.refund(id, r == null ? null : r.amountMinor(), r == null ? null : r.reason(), jwt.getSubject());
        PaymentOrder o = orders.get(id);
        return java.util.Map.of("refundId", refund.getId(), "amountMinor", refund.getAmountMinor(), "status", refund.getStatus(),
                "orderStatus", o.getStatus().name());
    }

    @PostMapping("/mandates")
    @ResponseStatus(HttpStatus.CREATED)
    MandateResponse createMandate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MandateRequest r) {
        return MandateResponse.from(mandates.create(userId(jwt), r.planCode(), r.billingPeriod(), r.currency(), r.method()));
    }

    @GetMapping("/mandates")
    List<MandateResponse> listMandates(@AuthenticationPrincipal Jwt jwt) {
        return mandates.list(userId(jwt)).stream().map(MandateResponse::from).toList();
    }

    @PostMapping("/mandates/{id}/cancel")
    MandateResponse cancelMandate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return MandateResponse.from(mandates.cancel(userId(jwt), id));
    }

    @PostMapping("/admin/reconciliation")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    List<java.util.Map<String, Object>> reconcile(@RequestParam(required = false) LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now(in.pahadijodi.payment.gateway.FakeGateway.IST);
        return reconciliation.runFor(d).stream().map(PaymentController::summary).toList();
    }

    @GetMapping("/admin/reconciliation")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    List<java.util.Map<String, Object>> runs() { return reconciliation.recent().stream().map(PaymentController::summary).toList(); }

    @GetMapping("/admin/reconciliation/{runId}/items")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    List<java.util.Map<String, Object>> runItems(@PathVariable UUID runId) {
        return reconciliation.itemsOf(runId).stream().map(i -> java.util.Map.<String, Object>of("kind", i.getKind(),
                "providerPaymentId", String.valueOf(i.getProviderPaymentId()), "note", String.valueOf(i.getNote()))).toList();
    }

    private static java.util.Map<String, Object> summary(ReconciliationRun r) {
        return java.util.Map.of("runId", r.getId(), "provider", r.getProvider(), "date", r.getBusinessDate().toString(),
                "matched", r.getMatched(), "mismatched", r.getMismatched(),
                "missingLocal", r.getMissingLocal(), "missingProvider", r.getMissingProvider());
    }

    private PaymentOrder owned(Jwt jwt, Authentication auth, UUID id) {
        PaymentOrder o = orders.get(id);
        boolean staff = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_FINANCE"));
        // 404 (not 403) for other people's payments: do not reveal that the id exists
        if (!o.getUserId().equals(userId(jwt)) && !staff) throw new PjException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found");
        return o;
    }
}
