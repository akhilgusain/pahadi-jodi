package in.pahadijodi.fraud.text;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Counts distinct romance-scam phrase families in a message (money, crypto, gift cards, urgent travel...). */
public final class ScamTextAnalyzer {
    private ScamTextAnalyzer() {}

    private static final List<Pattern> FAMILIES = List.of(
            p("send (me )?(some )?money|transfer (the )?money|wire (me|money)|western union|moneygram|paytm me|gpay me|google pay me|upi me"),
            p("bitcoin|crypto|usdt|binance|invest(ment)?( plan| scheme)?|trading (app|platform)|guaranteed returns?"),
            p("gift ?cards?|itunes card|google play card|amazon (voucher|card)"),
            p("urgent(ly)?|emergency|hospital|accident|stuck at (the )?airport|customs (fee|duty|charge)|visa fee|ticket money"),
            p("whatsapp me|telegram|move to (whatsapp|telegram)|text me on|my number is \\+?\\d"),
            p("(my )?(mother|father|brother|son) is (sick|in hospital)|medical bill|surgery"),
            p("i will (come|visit) (to )?india|parcel (is )?(held|stuck)|send (you )?a (gift|parcel)"));

    public static int hits(String text) {
        if (text == null || text.isBlank()) return 0;
        String t = text.toLowerCase(Locale.ROOT);
        int n = 0;
        for (Pattern f : FAMILIES) if (f.matcher(t).find()) n++;
        return n;
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex);
    }
}
