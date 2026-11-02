package in.pahadijodi.payment.service;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.payment.domain.Mandate;
import in.pahadijodi.payment.domain.MandateRepository;
import in.pahadijodi.payment.gateway.GatewayRegistry;
import in.pahadijodi.payment.service.PriceTable.Period;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STUB: stores the auto-renewal consent (UPI AutoPay / card token). Real provider mandate registration and the
 * RBI 24h pre-debit notification + recurring charge job are the next increment.
 */
@Service
public class MandateService {
    private final MandateRepository mandates;
    private final PriceTable prices;

    public MandateService(MandateRepository mandates, PriceTable prices) {
        this.mandates = mandates; this.prices = prices;
    }

    @Transactional
    public Mandate create(UUID userId, String planCode, Period period, String currency, String method) {
        long base = prices.price(planCode, period, currency).orElseThrow(() ->
                new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "PLAN_NOT_AVAILABLE", "No price for that plan"));
        long max = base + TaxCalculator.compute(currency, base, "05", "07").total();   // upper bound incl. worst-case IGST
        String provider = GatewayRegistry.providerFor(currency);
        var next = ZonedDateTime.now().plusMonths(period.months).toInstant();
        return mandates.save(new Mandate(userId, provider, "mandate_fake_" + UUID.randomUUID().toString().substring(0, 12),
                planCode, period.name(), method == null ? "UPI_AUTOPAY" : method, max, currency, next));
    }

    @Transactional(readOnly = true)
    public List<Mandate> list(UUID userId) { return mandates.findByUserIdOrderByCreatedAtDesc(userId); }

    @Transactional
    public Mandate cancel(UUID userId, UUID id) {
        Mandate m = mandates.findById(id).filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "MANDATE_NOT_FOUND", "Mandate not found"));
        m.cancel();
        return mandates.save(m);
    }

    @Transactional
    public int cancelAllFor(UUID userId) {
        var active = mandates.findByUserIdAndStatus(userId, "ACTIVE");
        active.forEach(Mandate::cancel);
        mandates.saveAll(active);
        return active.size();
    }
}
