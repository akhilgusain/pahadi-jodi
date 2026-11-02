package in.pahadijodi.airec.service;

import in.pahadijodi.airec.llm.Redactor;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Rule-based first line of defence for chat/profile text (toxicity, harassment, spam/scam, contact sharing). Transparent and
 * cheap; it is the SEED that a trained classifier / LLM judge replaces (docs/04 moderation-service). Lexicons are intentionally short.
 */
@Service
public class SafetyService {
    public record Result(String action, List<String> labels, Map<String, Double> scores, String reason) {}

    private static final Pattern TOXIC = Pattern.compile("(?i)\\b(idiot|stupid|moron|bastard|bitch|shut up|useless|bewakoof|harami|kameena|kutta|chutiya)\\b");
    private static final Pattern HARASS = Pattern.compile("(?i)(send (me )?(nudes?|pics? now)|i will find you|i know where you (live|stay)|reply (now|immediately)|why (are you )?ignoring|you have no choice|\\b(kill|rape|acid)\\b)");
    private static final Pattern SCAM = Pattern.compile("(?i)(send money|transfer (the )?(money|amount)|investment|crypto|guaranteed returns?|bitcoin|gift card|visa fees?|customs fees?|emergency|lottery|click (here|the link)|verify your (account|kyc)|otp)");
    private static final Pattern URL = Pattern.compile("(?i)(https?://|www\\.)\\S+|\\b\\w+\\.(com|in|xyz|top|club|link)\\b");
    private static final Pattern MOVE_OFF = Pattern.compile("(?i)(whats ?app|telegram|insta(gram)?|snap ?chat|call me|text me on)");

    private final Redactor redactor;

    public SafetyService(Redactor redactor) { this.redactor = redactor; }

    public Result check(String text) {
        String t = text == null ? "" : text;
        double toxicity = hits(TOXIC, t) == 0 ? 0 : Math.min(1.0, 0.45 + 0.25 * hits(TOXIC, t));
        double harassment = hits(HARASS, t) == 0 ? 0 : Math.min(1.0, 0.8 + 0.1 * (hits(HARASS, t) - 1));
        double scam = Math.min(1.0, 0.35 * hits(SCAM, t) + (URL.matcher(t).find() ? 0.25 : 0));
        double spam = spamScore(t);
        double contact = redactor.containsPii(t) || MOVE_OFF.matcher(t).find() ? 0.6 : 0;

        List<String> labels = new ArrayList<>();
        if (toxicity >= 0.4) labels.add("TOXICITY");
        if (harassment >= 0.4) labels.add("HARASSMENT");
        if (scam >= 0.5) labels.add("SCAM");
        if (spam >= 0.5) labels.add("SPAM");
        if (contact >= 0.5) labels.add("CONTACT_SHARING");

        String action = "ALLOW";
        String reason = "No issues detected";
        if (harassment >= 0.8 || toxicity >= 0.8 || scam >= 0.8) { action = "BLOCK"; reason = "Message looks abusive, threatening or like a scam"; }
        else if (!labels.isEmpty()) { action = "WARN"; reason = "Message may break community guidelines: " + String.join(", ", labels); }

        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("toxicity", round(toxicity)); scores.put("harassment", round(harassment));
        scores.put("scam", round(scam)); scores.put("spam", round(spam)); scores.put("contactSharing", round(contact));
        return new Result(action, labels, scores, reason);
    }

    private static double spamScore(String t) {
        if (t.isBlank()) return 0;
        double s = 0;
        if (t.matches("(?s).*(.)\\1{7,}.*")) s += 0.4;                                  // aaaaaaaa
        long letters = t.chars().filter(Character::isLetter).count();
        long upper = t.chars().filter(Character::isUpperCase).count();
        if (letters >= 20 && upper > letters * 0.7) s += 0.3;                           // SHOUTING
        if (hits(URL, t) >= 2) s += 0.4;
        if (t.length() > 40 && new HashSet<>(Arrays.asList(t.toLowerCase().split("\\s+"))).size() < t.split("\\s+").length / 3) s += 0.4;  // copy-paste repetition
        return Math.min(1.0, s);
    }

    private static int hits(Pattern p, String t) {
        int n = 0;
        var m = p.matcher(t);
        while (m.find()) n++;
        return n;
    }

    private static double round(double d) { return Math.round(d * 100) / 100.0; }
}
