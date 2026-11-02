package in.pahadijodi.airec.llm;

import static in.pahadijodi.airec.llm.Facts.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Grounded, templated compatibility narrative built from the numeric breakdown and the two (redacted) profiles. */
public final class CompatNarrative {
    private CompatNarrative() {}

    public record Narrative(String summary, List<String> strengths, List<String> discussionPoints, String communicationStyle, String disclaimer) {}

    @SuppressWarnings("unchecked")
    public static Narrative build(Map<String, Object> facts, CultureCatalog culture) {
        boolean hi = hi(facts);
        Map<String, Object> a = (Map<String, Object>) facts.getOrDefault("a", Map.of());
        Map<String, Object> b = (Map<String, Object>) facts.getOrDefault("b", Map.of());
        List<String> strengths = new ArrayList<>();
        List<String> points = new ArrayList<>();

        String da = str(a, "district"), db = str(b, "district");
        String va = str(a, "division"), vb = str(b, "division");
        if (da != null && da.equals(db)) {
            strengths.add(hi ? "आप दोनों " + culture.hindiDistrict(da) + " से हैं।" : "Both of you are from " + CultureCatalog.pretty(da) + ".");
        } else if (va != null && va.equals(vb)) {
            strengths.add(hi ? "आप दोनों " + culture.hindiDivision(va) + " क्षेत्र से हैं।" : "Both of you are from the " + CultureCatalog.pretty(va) + " region.");
        } else if (va != null && vb != null) {
            points.add(hi ? "आप अलग-अलग क्षेत्रों (" + culture.hindiDivision(va) + " और " + culture.hindiDivision(vb) + ") से हैं; बात करें कि भविष्य में कहाँ रहना चाहेंगे।"
                    : "You are from different regions (" + CultureCatalog.pretty(va) + " and " + CultureCatalog.pretty(vb) + "); it may help to talk about where you would like to live.");
        }
        if (str(a, "community") != null && str(a, "community").equals(str(b, "community"))) {
            strengths.add(hi ? "आपका सामुदायिक/भाषाई परिवेश मिलता-जुलता है।" : "You share a similar community and cultural background.");
        }
        String la = str(a, "language"), lb = str(b, "language");
        if (la != null && la.equalsIgnoreCase(lb)) {
            strengths.add(hi ? "दोनों की मातृभाषा " + la + " है।" : "Both of you speak " + la + ".");
        } else if (la != null && lb != null) {
            points.add(hi ? "आपकी मातृभाषाएँ अलग हैं; घर में कौन-सी भाषा बोलेंगे, इस पर चर्चा करें।" : "Your mother tongues differ; you may want to talk about the language you would use at home.");
        }
        if (Math.abs(num(a, "age", 0) - num(b, "age", 0)) <= 5 && num(a, "age", 0) > 0 && num(b, "age", 0) > 0) {
            strengths.add(hi ? "आपकी उम्र में अंतर कम है।" : "Your ages are close.");
        } else if (Math.abs(num(a, "age", 0) - num(b, "age", 0)) > 8 && num(a, "age", 0) > 0 && num(b, "age", 0) > 0) {
            points.add(hi ? "उम्र का अंतर कुछ अधिक है; अपेक्षाओं पर खुलकर बात करें।" : "There is a noticeable age difference; it helps to discuss expectations openly.");
        }
        if (str(a, "gotra") != null && str(a, "gotra").equalsIgnoreCase(str(b, "gotra"))) {
            points.add(hi ? "दोनों ने एक ही गोत्र बताया है; कई परिवार इसे शुरू में ही देखते हैं।" : "You listed the same gotra; many families prefer to check this early.");
        }
        if (strengths.isEmpty()) strengths.add(hi ? "दोनों प्रोफ़ाइल पूरी तरह उत्तराखंड से जुड़ी हैं।" : "Both profiles are rooted in Uttarakhand.");
        if (points.isEmpty()) points.add(hi ? "परिवार, काम और भविष्य की योजनाओं पर बात करना अच्छा रहेगा।" : "It would be good to talk about family, work and future plans.");

        int overall = num(facts, "overall", 0);
        String summary = hi
                ? "प्रोफ़ाइल की जानकारी के आधार पर आपका मेल " + overall + "/100 आँका गया है। यह केवल बातचीत शुरू करने में मदद के लिए है।"
                : "Based only on the profile details, the match score is " + overall + "/100. " + strengths.get(0) + " This is meant to help start a conversation.";
        return new Narrative(summary, strengths.stream().limit(3).toList(), points.stream().limit(3).toList(),
                hi ? "परिवार, काम और अपने शहर के बारे में सरल, सम्मानजनक सवालों से शुरुआत करें।"
                        : "Start with simple, respectful questions about hometown, work and family.",
                hi ? "यह केवल प्रोफ़ाइल जानकारी पर आधारित बातचीत का सहारा है, भविष्यवाणी नहीं।"
                        : "This is a conversation aid based only on profile details, not a prediction of compatibility.");
    }
}
