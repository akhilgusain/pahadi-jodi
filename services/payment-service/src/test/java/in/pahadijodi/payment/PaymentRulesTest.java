package in.pahadijodi.payment;

import static org.junit.jupiter.api.Assertions.*;

import in.pahadijodi.payment.domain.OrderStatus;
import in.pahadijodi.payment.service.InvoiceService;
import in.pahadijodi.payment.service.TaxCalculator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaymentRulesTest {
    @Test
    void gstSplitsIntoCgstSgstWithinUttarakhandAndIgstElsewhere() {
        var intra = TaxCalculator.compute("INR", 49_900, "05", "05");
        assertEquals(8_982, intra.total());
        assertEquals(4_491, intra.cgst());
        assertEquals(4_491, intra.sgst());
        assertEquals(0, intra.igst());
        var inter = TaxCalculator.compute("INR", 49_900, "05", "07");
        assertEquals(8_982, inter.igst());
        assertEquals(0, inter.cgst() + inter.sgst());
    }

    @Test
    void oddPaiseTaxStillSumsExactly() {
        var t = TaxCalculator.compute("INR", 10_001, "05", "05");   // 1800.18 -> 1800
        assertEquals(t.total(), t.cgst() + t.sgst());
    }

    @Test
    void exportOfServicesIsZeroRated() {
        assertEquals(0, TaxCalculator.compute("USD", 899, "05", "05").total());
    }

    @Test
    void orderStateMachineIsMonotonic() {
        assertTrue(OrderStatus.CREATED.canTransitionTo(OrderStatus.CAPTURED));
        assertTrue(OrderStatus.FAILED.canTransitionTo(OrderStatus.CAPTURED));        // retry on same order
        assertFalse(OrderStatus.CAPTURED.canTransitionTo(OrderStatus.FAILED));       // late failure event is ignored
        assertFalse(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.CAPTURED));
        assertTrue(OrderStatus.PARTIALLY_REFUNDED.canTransitionTo(OrderStatus.REFUNDED));
    }

    @Test
    void financialYearRollsOverInApril() {
        assertEquals("2026-27", InvoiceService.financialYear(LocalDate.of(2026, 11, 2)));
        assertEquals("2026-27", InvoiceService.financialYear(LocalDate.of(2027, 3, 31)));
        assertEquals("2027-28", InvoiceService.financialYear(LocalDate.of(2027, 4, 1)));
    }
}
