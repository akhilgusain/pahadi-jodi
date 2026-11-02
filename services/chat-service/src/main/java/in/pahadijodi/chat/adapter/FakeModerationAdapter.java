package in.pahadijodi.chat.adapter;

import in.pahadijodi.chat.config.ChatProperties;
import in.pahadijodi.chat.port.ModerationPort;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Local rule-based moderation: BLOCK abusive words (configurable), FLAG scam/contact-sharing patterns.
 * Production: call moderation-service (LLM + classifiers) with a tight timeout and fail-open + async re-check.
 */
@Component
public class FakeModerationAdapter implements ModerationPort {
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\s-]?){10,}");
    private static final List<String> SCAM_HINTS = List.of("send money", "gift card", "western union", "bitcoin", "upi pin");

    private final List<String> banned;

    public FakeModerationAdapter(ChatProperties props) {
        this.banned = props.moderation().bannedWords().stream().map(w -> w.toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    public Verdict check(UUID sender, String text) {
        if (text == null || text.isBlank()) return Verdict.allow();
        String t = text.toLowerCase(Locale.ROOT);
        for (String w : banned) {
            if (Pattern.compile("\\b" + Pattern.quote(w) + "\\b").matcher(t).find()) {
                return new Verdict(Action.BLOCK, "ABUSIVE_LANGUAGE");
            }
        }
        for (String hint : SCAM_HINTS) {
            if (t.contains(hint)) return new Verdict(Action.FLAG, "POSSIBLE_SCAM");
        }
        if (PHONE.matcher(text).find()) return new Verdict(Action.FLAG, "CONTACT_SHARING");
        return Verdict.allow();
    }
}
