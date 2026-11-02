package in.pahadijodi.payment.service;

import in.pahadijodi.payment.domain.*;
import in.pahadijodi.payment.domain.Invoice.Type;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final InvoiceSequenceRepository sequences;
    private final String sellerState;

    public InvoiceService(InvoiceRepository invoices, InvoiceSequenceRepository sequences,
                          @org.springframework.beans.factory.annotation.Value("${pj.payment.seller-state-code}") String sellerState) {
        this.invoices = invoices; this.sequences = sequences; this.sellerState = sellerState;
    }

    /** Issued inside the capture transaction so numbering is gap-free: a rollback releases the number too. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice issueTaxInvoice(PaymentOrder o) {
        boolean intra = o.getPlaceOfSupply().equals(sellerState);
        var tax = "INR".equals(o.getCurrency()) ? TaxCalculator.split(o.getTaxMinor(), intra) : TaxCalculator.Tax.NONE;
        return invoices.save(new Invoice(nextNumber(), o, Type.TAX_INVOICE, null, o.getAmountMinor(),
                tax.cgst(), tax.sgst(), tax.igst(), o.getTotalMinor()));
    }

    /** Credit note for a (partial) refund, proportionally reversing taxable value and tax of the original invoice. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice issueCreditNote(PaymentOrder o, long refundMinor) {
        Invoice original = invoices.findByOrderIdOrderByIssuedAt(o.getId()).stream()
                .filter(i -> i.getType() == Type.TAX_INVOICE).findFirst().orElse(null);
        long taxable = Math.round((double) refundMinor * o.getAmountMinor() / o.getTotalMinor());
        long tax = refundMinor - taxable;
        boolean intra = o.getPlaceOfSupply().equals(sellerState);
        var split = "INR".equals(o.getCurrency()) ? TaxCalculator.split(tax, intra) : TaxCalculator.Tax.NONE;
        return invoices.save(new Invoice(nextNumber(), o, Type.CREDIT_NOTE, original == null ? null : original.getId(),
                taxable, split.cgst(), split.sgst(), split.igst(), refundMinor));
    }

    private String nextNumber() {
        String fy = financialYear(ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate());
        InvoiceSequence seq = sequences.findWithLockByFy(fy).orElseGet(() -> sequences.saveAndFlush(new InvoiceSequence(fy)));
        long n = seq.next();
        sequences.save(seq);
        return "PJ/%s/%06d".formatted(fy, n);
    }

    /** Indian FY runs April to March: 2026-11-02 is "2026-27", 2027-02-10 is also "2026-27". */
    public static String financialYear(LocalDate d) {
        int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return "%d-%02d".formatted(start, (start + 1) % 100);
    }

    @Transactional(readOnly = true)
    public List<Invoice> forOrder(UUID orderId) { return invoices.findByOrderIdOrderByIssuedAt(orderId); }

    /** Minimal printable HTML (a PDF renderer can consume this later). */
    public String renderHtml(PaymentOrder o, List<Invoice> list) {
        StringBuilder sb = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><title>Invoice</title>"
                + "<style>body{font-family:sans-serif;max-width:640px;margin:2rem auto}table{border-collapse:collapse;width:100%}"
                + "td,th{border-bottom:1px solid #ddd;padding:6px;text-align:left}td.n{text-align:right}</style></head><body>");
        for (Invoice i : list) {
            sb.append("<h2>").append(i.getType() == Type.TAX_INVOICE ? "Tax Invoice" : "Credit Note").append(" ")
                    .append(HtmlUtils.htmlEscape(i.getInvoiceNo())).append("</h2>")
                    .append("<p>Pahadi Jodi &middot; Place of supply: ").append(HtmlUtils.htmlEscape(i.getPlaceOfSupply()))
                    .append(" &middot; SAC ").append(i.getHsnSac()).append("</p><table>")
                    .append("<tr><th>Description</th><th class=n>Amount</th></tr>")
                    .append(row(o.getPlanCode() + " (" + o.getBillingPeriod() + ")", i.getTaxableMinor(), i.getCurrency()));
            if (i.getCgstMinor() > 0) sb.append(row("CGST 9%", i.getCgstMinor(), i.getCurrency()));
            if (i.getSgstMinor() > 0) sb.append(row("SGST 9%", i.getSgstMinor(), i.getCurrency()));
            if (i.getIgstMinor() > 0) sb.append(row("IGST 18%", i.getIgstMinor(), i.getCurrency()));
            sb.append(row("<b>Total</b>", i.getTotalMinor(), i.getCurrency())).append("</table><hr>");
        }
        return sb.append("</body></html>").toString();
    }

    private static String row(String label, long minor, String currency) {
        return "<tr><td>" + label + "</td><td class=n>" + currency + " " + String.format("%d.%02d", minor / 100, minor % 100) + "</td></tr>";
    }
}
