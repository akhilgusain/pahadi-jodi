package in.pahadijodi.fraud.text;

import java.util.Locale;
import java.util.Set;

public final class DisposableEmails {
    private DisposableEmails() {}

    private static final Set<String> DOMAINS = Set.of(
            "mailinator.com", "guerrillamail.com", "10minutemail.com", "tempmail.com", "temp-mail.org", "yopmail.com",
            "trashmail.com", "throwawaymail.com", "getnada.com", "sharklasers.com", "dispostable.com", "maildrop.cc",
            "fakeinbox.com", "mintemail.com", "mohmal.com", "emailondeck.com", "tempinbox.com", "spamgourmet.com");

    public static boolean isDisposable(String domain) {
        return domain != null && DOMAINS.contains(domain.trim().toLowerCase(Locale.ROOT));
    }
}
