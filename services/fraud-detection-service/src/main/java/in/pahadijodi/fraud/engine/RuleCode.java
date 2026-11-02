package in.pahadijodi.fraud.engine;

import java.util.function.ToDoubleFunction;

/** Each rule maps facts to a fraction in [0,1]; points = round(weight * fraction). Weights live in the fraud_rule table. */
public enum RuleCode {
    IP_VELOCITY(25, "Several accounts from one IP within an hour",
            f -> f.accountsFromIp1h() <= 1 ? 0 : f.accountsFromIp1h() == 2 ? 0.5 : 1),
    DEVICE_MULTI_ACCOUNT(30, "One device used by several accounts",
            f -> f.accountsFromDevice() <= 1 ? 0 : f.accountsFromDevice() == 2 ? 0.5 : 1),
    DISPOSABLE_EMAIL(20, "Disposable e-mail domain", f -> f.disposableEmail() ? 1 : 0),
    DUPLICATE_PROFILE_TEXT(25, "Profile text duplicates another account", f -> f.duplicateProfileText() ? 1 : 0),
    DUPLICATE_PHOTO(35, "Photo duplicates another account's photo", f -> f.duplicatePhoto() ? 1 : 0),
    SCAM_KEYWORDS(40, "Romance-scam phrases in chat", f -> Math.min(1.0, f.scamHits24h() / 3.0)),
    MESSAGE_BLAST(30, "Messaging many distinct users within an hour",
            f -> f.distinctRecipients1h() < 5 ? 0 : Math.min(1.0, f.distinctRecipients1h() / 15.0)),
    PAYMENT_FAILURES(25, "Repeated failed payments (card testing)",
            f -> f.paymentFailures24h() < 2 ? 0 : Math.min(1.0, f.paymentFailures24h() / 4.0)),
    CHARGEBACK(40, "Chargeback or payment dispute", f -> f.chargebacks() > 0 ? 1 : 0);

    private final int defaultWeight;
    private final String description;
    private final ToDoubleFunction<Facts> fraction;

    RuleCode(int defaultWeight, String description, ToDoubleFunction<Facts> fraction) {
        this.defaultWeight = defaultWeight;
        this.description = description;
        this.fraction = fraction;
    }

    public int defaultWeight() { return defaultWeight; }
    public String description() { return description; }
    public double fraction(Facts f) { return fraction.applyAsDouble(f); }
}
