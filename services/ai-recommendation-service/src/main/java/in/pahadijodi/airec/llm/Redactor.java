package in.pahadijodi.airec.llm;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Removes contact details and government ids from anything that leaves the service towards an LLM (and from LLM output). */
@Component
public class Redactor {
    private record Rule(Pattern pattern, String replacement) {}

    // order matters: most specific first
    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[EMAIL]"),
            new Rule(Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+"), "[LINK]"),
            new Rule(Pattern.compile("(?<![\\w@])@[A-Za-z0-9_.]{3,}"), "[HANDLE]"),
            new Rule(Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b"), "[ID]"),                                   // PAN
            new Rule(Pattern.compile("(?<!\\d)[2-9]\\d{3}[\\s-]?\\d{4}[\\s-]?\\d{4}(?!\\d)"), "[ID]"),          // Aadhaar
            new Rule(Pattern.compile("(?<!\\d)(?:\\+?91[\\s-]?)?[6-9]\\d{4}[\\s-]?\\d{5}(?!\\d)"), "[PHONE]"),   // Indian mobile
            new Rule(Pattern.compile("(?<!\\d)\\d{9,}(?!\\d)"), "[NUMBER]"));

    public String redact(String text) {
        if (text == null) return null;
        String out = text;
        for (Rule r : RULES) out = r.pattern().matcher(out).replaceAll(r.replacement());
        return out;
    }

    public boolean containsPii(String text) {
        if (text == null) return false;
        return RULES.stream().anyMatch(r -> r.pattern().matcher(text).find());
    }

    /** Deep-redacts string values inside maps/lists (used for the structured facts). */
    @SuppressWarnings("unchecked")
    public Object redactValue(Object v) {
        if (v instanceof String s) return redact(s);
        if (v instanceof Map<?, ?> m) {
            var out = new java.util.LinkedHashMap<String, Object>();
            ((Map<String, Object>) m).forEach((k, x) -> out.put(k, redactValue(x)));
            return out;
        }
        if (v instanceof List<?> l) return l.stream().map(this::redactValue).toList();
        return v;
    }
}
