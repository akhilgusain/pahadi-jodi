package in.pahadijodi.moderation.adapter;

import in.pahadijodi.moderation.port.ContentClassifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Transparent rule-based stand-in. Labels: TOXICITY, HARASSMENT, SEXUAL_CONTENT, SCAM, CONTACT_SHARING,
 * SPAM, LINK, SAMPLE_REVIEW. It is a safety net with known gaps (obfuscation, other languages), not a model.
 */
@Component
@ConditionalOnProperty(name = "pj.moderation.classifier.provider", havingValue = "fake", matchIfMissing = true)
public class RuleBasedClassifier implements ContentClassifier {
    private static final List<String> TOXIC_HARD = List.of("bastard", "slut", "whore", "harami", "haramzada", "chutiya",
            "madarchod", "behenchod", "kill you", "rape");
    private static final List<String> TOXIC_MILD = List.of("idiot", "stupid", "fool", "bewakoof", "kamina");
    private static final List<String> HARASSMENT = List.of("i will find you", "you will regret", "nobody will marry you");
    private static final List<String> SEXUAL = List.of("nude", "nudes", "sexting", "sex chat", "hookup", "one night stand");
    private static final List<String> SCAM = List.of("western union", "gift card", "bitcoin", "crypto", "wire transfer",
            "lottery", "send money", "investment opportunity", "bank details");
    private static final Pattern PHONE = Pattern.compile("(?:\\+?\\d[\\s-]?){10,}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern LINK = Pattern.compile("(?i)(https?://|www\\.)\\S+");
    private static final Pattern CHAR_RUN = Pattern.compile("(.)\\1{7,}");

    private final double imageSampleRate;

    public RuleBasedClassifier(@Value("${pj.moderation.image.sample-rate:0.1}") double imageSampleRate) {
        this.imageSampleRate = imageSampleRate;
    }

    @Override
    public List<Label> classifyText(String text) {
        List<Label> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        String t = text.toLowerCase(Locale.ROOT);

        if (hasWord(t, TOXIC_HARD)) out.add(new Label("TOXICITY", 0.90));
        else if (hasWord(t, TOXIC_MILD)) out.add(new Label("TOXICITY", 0.60));
        if (containsAny(t, HARASSMENT)) out.add(new Label("HARASSMENT", 0.75));
        if (hasWord(t, SEXUAL)) out.add(new Label("SEXUAL_CONTENT", 0.85));
        if (containsAny(t, SCAM)) out.add(new Label("SCAM", 0.80));
        if (PHONE.matcher(text).find() || EMAIL.matcher(text).find()) out.add(new Label("CONTACT_SHARING", 0.70));
        if (LINK.matcher(text).find()) out.add(new Label("LINK", 0.45));      // visible to reviewers, below the flag threshold
        if (isSpam(text, t)) out.add(new Label("SPAM", 0.60));

        out.sort(Comparator.comparingDouble(Label::score).reversed());
        return out;
    }

    /** Deterministic sampling: the same refId is always (or never) selected. */
    @Override
    public List<Label> classifyImage(String refId) {
        if (refId != null && imageSampleRate > 0 && bucket(refId) < imageSampleRate) {
            return List.of(new Label("SAMPLE_REVIEW", 0.50));
        }
        return List.of();
    }

    private static boolean hasWord(String t, List<String> words) {
        for (String w : words) {
            if (Pattern.compile("(?<![a-z])" + Pattern.quote(w) + "(?![a-z])").matcher(t).find()) return true;
        }
        return false;
    }

    private static boolean containsAny(String t, List<String> phrases) {
        for (String p : phrases) if (t.contains(p)) return true;
        return false;
    }

    private static boolean isSpam(String original, String lower) {
        if (CHAR_RUN.matcher(lower).find()) return true;
        String[] tokens = lower.split("\\W+");
        if (tokens.length >= 5) {
            var counts = new java.util.HashMap<String, Integer>();
            for (String tk : tokens) if (!tk.isBlank()) counts.merge(tk, 1, Integer::sum);
            if (counts.values().stream().anyMatch(c -> c >= 5)) return true;
        }
        long letters = original.chars().filter(Character::isLetter).count();
        long upper = original.chars().filter(Character::isUpperCase).count();
        return letters >= 20 && upper > letters * 0.7;
    }

    private static double bucket(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return (((h[0] & 0xff) << 8) | (h[1] & 0xff)) / 65536.0;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
