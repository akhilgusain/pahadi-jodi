package in.pahadijodi.payment.service;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Server-side price list (base price, exclusive of GST). Amounts in minor units: paise for INR, cents for USD. */
@Component
public class PriceTable {
    public enum Period {
        MONTHLY(1), QUARTERLY(3), ANNUAL(12);
        public final int months;
        Period(int months) { this.months = months; }
    }

    private static final Map<String, Long> PRICES = Map.ofEntries(
            Map.entry("PREMIUM:MONTHLY:INR", 49_900L), Map.entry("PREMIUM:QUARTERLY:INR", 119_900L), Map.entry("PREMIUM:ANNUAL:INR", 399_900L),
            Map.entry("PREMIUM_PLUS:MONTHLY:INR", 99_900L), Map.entry("PREMIUM_PLUS:QUARTERLY:INR", 249_900L), Map.entry("PREMIUM_PLUS:ANNUAL:INR", 799_900L),
            Map.entry("VIP:MONTHLY:INR", 299_900L), Map.entry("VIP:QUARTERLY:INR", 749_900L), Map.entry("VIP:ANNUAL:INR", 2_499_900L),
            Map.entry("PREMIUM:MONTHLY:USD", 899L), Map.entry("PREMIUM:QUARTERLY:USD", 2_299L), Map.entry("PREMIUM:ANNUAL:USD", 6_999L),
            Map.entry("PREMIUM_PLUS:MONTHLY:USD", 1_499L), Map.entry("PREMIUM_PLUS:QUARTERLY:USD", 3_799L), Map.entry("PREMIUM_PLUS:ANNUAL:USD", 12_999L),
            Map.entry("VIP:MONTHLY:USD", 3_999L), Map.entry("VIP:QUARTERLY:USD", 9_999L), Map.entry("VIP:ANNUAL:USD", 34_999L));

    public Optional<Long> price(String plan, Period period, String currency) {
        return Optional.ofNullable(PRICES.get(plan + ":" + period + ":" + currency));
    }
}
