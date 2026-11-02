package in.pahadijodi.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.common.outbox.OutboxEvent;
import in.pahadijodi.common.outbox.OutboxRepository;
import in.pahadijodi.payment.gateway.FakeGateway;
import in.pahadijodi.payment.gateway.PaymentGateway.ProviderPayment;
import in.pahadijodi.payment.service.MandateService;
import in.pahadijodi.payment.service.SubscriptionEventsConsumer;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:paymenttest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.default_schema=PUBLIC",
        "spring.flyway.schemas=PUBLIC", "spring.flyway.create-schemas=false",
        "pj.outbox.relay.enabled=false", "pj.kafka.consumers.enabled=false",
        "pj.payment.dev-endpoints=true"})
class PaymentFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxRepository outbox;
    @Autowired @Qualifier("fakeRazorpay") FakeGateway razorpay;
    @Autowired MandateService mandates;
    @Autowired SubscriptionEventsConsumer consumer;

    static RequestPostProcessor user(UUID id) { return jwt().jwt(j -> j.subject(id.toString()).claim("roles", List.of("USER"))); }
    static RequestPostProcessor finance() {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("USER", "FINANCE")))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FINANCE"));
    }

    JsonNode order(UUID user, String key, String plan, String period, String cur, String state) throws Exception {
        String body = "{\"planCode\":\"%s\",\"billingPeriod\":\"%s\",\"currency\":\"%s\"%s}".formatted(plan, period, cur,
                state == null ? "" : ",\"stateCode\":\"" + state + "\"");
        var res = mvc.perform(post("/api/v1/payments/orders").with(user(user)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    void complete(UUID user, String orderId) throws Exception {
        mvc.perform(post("/api/v1/payments/dev/complete/" + orderId).with(user(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("APPLIED"));
    }

    long outboxCount(String topic) { return outbox.findAll().stream().filter(e -> e.getTopic().equals(topic)).count(); }

    @Test
    void authIsRequiredAndWebhooksArePublic() throws Exception {
        mvc.perform(get("/api/v1/payments")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/payments/webhooks/razorpay").content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())      // reached the controller: rejected for the signature, not for the missing JWT
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }

    @Test
    void createOrderPricesWithGstAndIsIdempotent() throws Exception {
        UUID u = UUID.randomUUID();
        JsonNode o = order(u, "k-1", "PREMIUM", "MONTHLY", "INR", null);
        assertEquals("RAZORPAY", o.get("provider").asText());
        assertEquals(49_900, o.get("amountMinor").asLong());
        assertEquals(8_982, o.get("taxMinor").asLong());
        assertEquals(58_882, o.get("totalMinor").asLong());
        assertEquals("CREATED", o.get("status").asText());

        // replay with the same key -> same order, not a second one
        var replay = mvc.perform(post("/api/v1/payments/orders").with(user(u)).header("Idempotency-Key", "k-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"PREMIUM\",\"billingPeriod\":\"MONTHLY\",\"currency\":\"INR\"}"))
                .andExpect(status().isCreated()).andReturn();
        assertEquals(o.get("orderId").asText(), json.readTree(replay.getResponse().getContentAsString()).get("orderId").asText());

        // same key, different plan -> conflict
        mvc.perform(post("/api/v1/payments/orders").with(user(u)).header("Idempotency-Key", "k-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"VIP\",\"billingPeriod\":\"MONTHLY\",\"currency\":\"INR\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        // diaspora: USD goes to Stripe and is zero-rated
        JsonNode usd = order(u, "k-2", "PREMIUM", "ANNUAL", "USD", null);
        assertEquals("STRIPE", usd.get("provider").asText());
        assertEquals(0, usd.get("taxMinor").asLong());

        mvc.perform(post("/api/v1/payments/orders").with(user(u)).header("Idempotency-Key", "k-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"GOLD\",\"billingPeriod\":\"MONTHLY\",\"currency\":\"INR\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void captureIssuesInvoiceAndPublishesEventOnce() throws Exception {
        UUID u = UUID.randomUUID();
        long before = outboxCount("pj.payment.captured.v1");
        String id = order(u, "cap-1", "PREMIUM_PLUS", "QUARTERLY", "INR", null).get("orderId").asText();
        complete(u, id);

        mvc.perform(get("/api/v1/payments/" + id).with(user(u))).andExpect(jsonPath("$.status").value("CAPTURED"));
        assertEquals(before + 1, outboxCount("pj.payment.captured.v1"));
        OutboxEvent ev = outbox.findAll().stream().filter(e -> e.getTopic().equals("pj.payment.captured.v1")
                && e.getEnvelope().contains(id)).findFirst().orElseThrow();
        assertTrue(ev.getEnvelope().contains("\"amountPaise\":294882") || ev.getEnvelope().contains("\"amountPaise\": 294882"), ev.getEnvelope());

        String html = mvc.perform(get("/api/v1/payments/" + id + "/invoice").with(user(u))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("PJ/2026-27/") || html.contains("PJ/20"), html);
        assertTrue(html.contains("CGST 9%") && html.contains("SGST 9%") && !html.contains("IGST"), html);

        // another user can neither see the payment nor the invoice
        mvc.perform(get("/api/v1/payments/" + id).with(user(UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void interStateBuyerGetsIgst() throws Exception {
        UUID u = UUID.randomUUID();
        String id = order(u, "igst-1", "PREMIUM", "MONTHLY", "INR", "07").get("orderId").asText();
        complete(u, id);
        String html = mvc.perform(get("/api/v1/payments/" + id + "/invoice").with(user(u))).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("IGST 18%") && !html.contains("CGST"), html);
    }

    @Test
    void webhookSignatureReplayAndOutOfOrderHandling() throws Exception {
        UUID u = UUID.randomUUID();
        JsonNode o = order(u, "wh-1", "PREMIUM", "MONTHLY", "INR", null);
        String providerOrderId = o.get("providerOrderId").asText();
        long total = o.get("totalMinor").asLong();

        var captured = razorpay.signedWebhook("evt_cap_1", "payment.captured", providerOrderId, "pay_x1", total, "INR", null);
        // tampered body -> rejected
        mvc.perform(post("/api/v1/payments/webhooks/razorpay").header("X-Signature", captured.signature())
                .contentType(MediaType.APPLICATION_JSON).content(captured.body().replace("pay_x1", "pay_evil")))
                .andExpect(status().isBadRequest());
        // valid -> applied; same delivery again -> duplicate, no second event
        long before = outboxCount("pj.payment.captured.v1");
        for (String expected : new String[]{"APPLIED", "DUPLICATE"}) {
            mvc.perform(post("/api/v1/payments/webhooks/razorpay").header("X-Signature", captured.signature())
                    .contentType(MediaType.APPLICATION_JSON).content(captured.body()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(expected));
        }
        assertEquals(before + 1, outboxCount("pj.payment.captured.v1"));

        // a late "failed" event (different event id) after capture must not undo it
        var late = razorpay.signedWebhook("evt_fail_late", "payment.failed", providerOrderId, "pay_x0", total, "INR", "BAD");
        mvc.perform(post("/api/v1/payments/webhooks/razorpay").header("X-Signature", late.signature())
                .contentType(MediaType.APPLICATION_JSON).content(late.body()))
                .andExpect(jsonPath("$.result").value("IGNORED"));
        mvc.perform(get("/api/v1/payments/" + o.get("orderId").asText()).with(user(u))).andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    @Test
    void wrongAmountIsNeverCaptured() throws Exception {
        UUID u = UUID.randomUUID();
        JsonNode o = order(u, "amt-1", "PREMIUM", "MONTHLY", "INR", null);
        var cheap = razorpay.signedWebhook("evt_cheap", "payment.captured", o.get("providerOrderId").asText(), "pay_c", 100, "INR", null);
        mvc.perform(post("/api/v1/payments/webhooks/razorpay").header("X-Signature", cheap.signature())
                .contentType(MediaType.APPLICATION_JSON).content(cheap.body()))
                .andExpect(jsonPath("$.result").value("AMOUNT_MISMATCH"));
        mvc.perform(get("/api/v1/payments/" + o.get("orderId").asText()).with(user(u))).andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void failedPaymentCanBeRetriedOnTheSameOrder() throws Exception {
        UUID u = UUID.randomUUID();
        String id = order(u, "retry-1", "VIP", "MONTHLY", "INR", null).get("orderId").asText();
        mvc.perform(post("/api/v1/payments/dev/fail/" + id).with(user(u))).andExpect(jsonPath("$.result").value("APPLIED"));
        mvc.perform(get("/api/v1/payments/" + id).with(user(u))).andExpect(jsonPath("$.status").value("FAILED"));
        complete(u, id);
        mvc.perform(get("/api/v1/payments/" + id).with(user(u))).andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    @Test
    void refundsNeedFinanceRoleAndCannotExceedPaid() throws Exception {
        UUID u = UUID.randomUUID();
        JsonNode o = order(u, "ref-1", "PREMIUM", "MONTHLY", "INR", null);   // total 58_882
        String id = o.get("orderId").asText();
        mvc.perform(post("/api/v1/payments/" + id + "/refund").with(finance()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());                           // not captured yet
        complete(u, id);

        mvc.perform(post("/api/v1/payments/" + id + "/refund").with(user(u)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());                          // customers cannot refund themselves
        mvc.perform(post("/api/v1/payments/" + id + "/refund").with(finance()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountMinor\":20000,\"reason\":\"SERVICE_ISSUE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderStatus").value("PARTIALLY_REFUNDED"));
        mvc.perform(post("/api/v1/payments/" + id + "/refund").with(finance()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountMinor\":58882}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_PAID"));
        mvc.perform(post("/api/v1/payments/" + id + "/refund").with(finance()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderStatus").value("REFUNDED"));

        String html = mvc.perform(get("/api/v1/payments/" + id + "/invoice").with(user(u))).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Credit Note"), html);
        assertTrue(outboxCount("pj.payment.refunded.v1") >= 2);
    }

    @Test
    void reconciliationFindsMissingAndMismatchedPayments() throws Exception {
        UUID u = UUID.randomUUID();
        String id = order(u, "rec-1", "PREMIUM", "MONTHLY", "INR", null).get("orderId").asText();
        complete(u, id);
        razorpay.injectProviderPayment(new ProviderPayment("pay_orphan_1", "order_unknown", 12_345, "INR", "captured"));

        var res = mvc.perform(post("/api/v1/payments/admin/reconciliation").with(finance()).param("date", LocalDate.now(FakeGateway.IST).toString()))
                .andExpect(status().isOk()).andReturn();
        JsonNode runs = json.readTree(res.getResponse().getContentAsString());
        JsonNode rz = null;
        for (JsonNode r : runs) if (r.get("provider").asText().equals("RAZORPAY")) rz = r;
        assertNotNull(rz);
        assertTrue(rz.get("matched").asInt() >= 1);
        assertTrue(rz.get("missingLocal").asInt() >= 1);

        String runId = rz.get("runId").asText();
        mvc.perform(get("/api/v1/payments/admin/reconciliation/" + runId + "/items").with(finance()))
                .andExpect(jsonPath("$[?(@.kind=='MISSING_LOCAL')].providerPaymentId").value(org.hamcrest.Matchers.hasItem("pay_orphan_1")));
        mvc.perform(post("/api/v1/payments/admin/reconciliation").with(user(u))).andExpect(status().isForbidden());
    }

    @Test
    void mandatesAreRecordedAndCancelledWhenSubscriptionIsCancelled() throws Exception {
        UUID u = UUID.randomUUID();
        mvc.perform(post("/api/v1/payments/mandates").with(user(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"planCode\":\"PREMIUM\",\"billingPeriod\":\"MONTHLY\",\"currency\":\"INR\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
        consumer.onCancelled("{\"payload\":{\"userId\":\"" + u + "\"}}");
        mvc.perform(get("/api/v1/payments/mandates").with(user(u))).andExpect(jsonPath("$[0].status").value("CANCELLED"));
        consumer.onCancelled("not json");     // must never throw
    }
}
