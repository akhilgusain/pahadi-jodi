package in.pahadijodi.payment.service;

/** GST for digital services (SAC 998439) at 18%. India: CGST+SGST intra-state, IGST inter-state. Foreign currency: export, zero-rated. */
public final class TaxCalculator {
    private TaxCalculator() {}

    public static final int GST_PERCENT = 18;

    public record Tax(long total, long cgst, long sgst, long igst) {
        public static final Tax NONE = new Tax(0, 0, 0, 0);
    }

    public static Tax compute(String currency, long baseMinor, String sellerState, String buyerState) {
        if (!"INR".equals(currency)) return Tax.NONE;
        long tax = (baseMinor * GST_PERCENT + 50) / 100;               // round half up to the paisa
        return split(tax, sellerState.equals(buyerState));
    }

    public static Tax split(long tax, boolean intraState) {
        if (intraState) {
            long cgst = tax / 2;
            return new Tax(tax, cgst, tax - cgst, 0);
        }
        return new Tax(tax, 0, 0, tax);
    }
}
