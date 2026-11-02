package in.pahadijodi.payment.service;

import in.pahadijodi.payment.domain.*;
import in.pahadijodi.payment.gateway.FakeGateway;
import in.pahadijodi.payment.gateway.GatewayRegistry;
import in.pahadijodi.payment.gateway.PaymentGateway;
import in.pahadijodi.payment.gateway.PaymentGateway.ProviderPayment;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compares captured payments we hold against the provider's own report for a business day (IST). */
@Service
public class ReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final GatewayRegistry gateways;
    private final PaymentOrderRepository orders;
    private final ReconciliationRunRepository runs;
    private final ReconciliationItemRepository items;
    private final boolean enabled;

    public ReconciliationService(GatewayRegistry gateways, PaymentOrderRepository orders, ReconciliationRunRepository runs,
                                 ReconciliationItemRepository items, @Value("${pj.payment.reconciliation.enabled:true}") boolean enabled) {
        this.gateways = gateways; this.orders = orders; this.runs = runs; this.items = items; this.enabled = enabled;
    }

    @Scheduled(cron = "${pj.payment.reconciliation.cron:0 30 20 * * *}")
    public void nightly() {
        if (!enabled) return;
        // TODO: take a distributed lock (ShedLock) before running when more than one pod is deployed
        runFor(LocalDate.now(FakeGateway.IST).minusDays(1));
    }

    @Transactional
    public List<ReconciliationRun> runFor(LocalDate date) {
        List<ReconciliationRun> result = new ArrayList<>();
        var from = date.atStartOfDay(FakeGateway.IST).toInstant();
        var to = date.plusDays(1).atStartOfDay(FakeGateway.IST).toInstant().minusMillis(1);
        for (PaymentGateway gw : gateways.all()) {
            List<ProviderPayment> remote = gw.fetchPayments(date);
            Map<String, PaymentOrder> local = new HashMap<>();
            for (PaymentOrder o : orders.findByProviderAndCapturedAtBetween(gw.provider(), from, to)) {
                if (o.getProviderPaymentId() != null) local.put(o.getProviderPaymentId(), o);
            }
            int matched = 0, mismatched = 0, missingLocal = 0, missingProvider = 0;
            List<ReconciliationItem> found = new ArrayList<>();
            var run = new ReconciliationRun(gw.provider(), date, 0, 0, 0, 0);
            for (ProviderPayment p : remote) {
                PaymentOrder o = local.remove(p.providerPaymentId());
                if (o == null) {
                    missingLocal++;
                    found.add(new ReconciliationItem(run.getId(), p.providerPaymentId(), "MISSING_LOCAL", null, p.amountMinor(),
                            "Provider captured, we have no record (webhook lost?)"));
                } else if (o.getTotalMinor() != p.amountMinor() || !o.getCurrency().equalsIgnoreCase(p.currency())) {
                    mismatched++;
                    found.add(new ReconciliationItem(run.getId(), p.providerPaymentId(), "MISMATCH_AMOUNT",
                            o.getTotalMinor(), p.amountMinor(), "Amount or currency differs"));
                } else {
                    matched++;
                }
            }
            for (PaymentOrder o : local.values()) {
                missingProvider++;
                found.add(new ReconciliationItem(run.getId(), o.getProviderPaymentId(), "MISSING_PROVIDER",
                        o.getTotalMinor(), null, "We marked captured but provider has no such payment"));
            }
            run.record(matched, mismatched, missingLocal, missingProvider);
            runs.save(run);
            for (ReconciliationItem i : found) {
                items.save(i);
                log.warn("Reconciliation {} {} {}: {}", gw.provider(), date, i.getKind(), i.getProviderPaymentId());
            }
            result.add(run);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ReconciliationRun> recent() { return runs.findTop20ByOrderByStartedAtDesc(); }

    @Transactional(readOnly = true)
    public List<ReconciliationItem> itemsOf(java.util.UUID runId) { return items.findByRunId(runId); }
}
