package in.pahadijodi.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.payment.domain.PaymentOrder;
import in.pahadijodi.payment.domain.PaymentOrderRepository;
import in.pahadijodi.payment.gateway.GatewayRegistry;
import in.pahadijodi.payment.gateway.PaymentGateway;
import in.pahadijodi.payment.service.PriceTable.Period;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    public record CreateOrderCommand(String planCode, Period billingPeriod, String currency, String stateCode) {}
    public record CreatedOrder(PaymentOrder order, Map<String, Object> clientParams) {}

    private final PaymentOrderRepository orders;
    private final GatewayRegistry gateways;
    private final PriceTable prices;
    private final ObjectMapper mapper;
    private final String sellerState;

    public OrderService(PaymentOrderRepository orders, GatewayRegistry gateways, PriceTable prices, ObjectMapper mapper,
                        @Value("${pj.payment.seller-state-code}") String sellerState) {
        this.orders = orders; this.gateways = gateways; this.prices = prices; this.mapper = mapper; this.sellerState = sellerState;
    }

    /** Idempotent per (user, key): replays return the original order; the same key with a different request is a 409. */
    @Transactional
    public CreatedOrder create(UUID userId, String idempotencyKey, CreateOrderCommand c) {
        var existing = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            PaymentOrder o = existing.get();
            boolean same = o.getPlanCode().equals(c.planCode()) && o.getBillingPeriod().equals(c.billingPeriod().name())
                    && o.getCurrency().equals(c.currency());
            if (!same) throw new PjException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "Idempotency key was used for a different request");
            return new CreatedOrder(o, readParams(o.getClientParams()));
        }
        long base = prices.price(c.planCode(), c.billingPeriod(), c.currency()).orElseThrow(() ->
                new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "PLAN_NOT_AVAILABLE", "No price for " + c.planCode() + "/" + c.billingPeriod() + "/" + c.currency()));
        String state = c.stateCode() == null || c.stateCode().isBlank() ? sellerState : c.stateCode();
        var tax = TaxCalculator.compute(c.currency(), base, sellerState, state);

        String provider = GatewayRegistry.providerFor(c.currency());
        UUID id = UUID.randomUUID();
        PaymentOrder order = new PaymentOrder(id, userId, c.planCode(), c.billingPeriod().name(), provider, c.currency(),
                base, tax.total(), state, idempotencyKey);
        // NOTE: provider call inside the DB tx keeps the skeleton simple; at scale create the row first (CREATED) and call the provider after commit.
        PaymentGateway.ProviderOrder po = gateways.get(provider).createOrder(
                new PaymentGateway.CreateOrder(id, order.getTotalMinor(), c.currency(), "pj_" + id.toString().substring(0, 8)));
        order.attachProviderOrder(po.providerOrderId(), writeParams(po.clientParams()));
        orders.save(order);
        return new CreatedOrder(order, po.clientParams());
    }

    @Transactional(readOnly = true)
    public PaymentOrder get(UUID id) {
        return orders.findById(id).orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
    }

    public Map<String, Object> readParams(String json) {
        try { return json == null ? Map.of() : mapper.readValue(json, new TypeReference<>() {}); }
        catch (JsonProcessingException e) { return Map.of(); }
    }

    private String writeParams(Map<String, Object> params) {
        try { return mapper.writeValueAsString(params); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
