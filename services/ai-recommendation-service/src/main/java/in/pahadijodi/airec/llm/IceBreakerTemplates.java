package in.pahadijodi.airec.llm;

import static in.pahadijodi.airec.llm.Facts.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Curated, vetted conversation starters keyed by what the two people actually share. No flirting, no questions about money,
 * horoscope, contact details or photos. Used by the fake LLM and as the fallback library.
 */
public final class IceBreakerTemplates {
    private IceBreakerTemplates() {}

    public static List<String> generate(Map<String, Object> common, CultureCatalog culture) {
        boolean hi = hi(common);
        boolean formal = "formal".equals(str(common, "tone"));
        String hello = hi ? (formal ? "नमस्ते! " : "हैलो! ") : (formal ? "Namaste! " : "Hi! ");
        String district = str(common, "sameDistrict");
        String division = str(common, "sameDivision");
        String language = str(common, "sameLanguage");
        List<String> festivals = list(common, "festivals");

        List<String> out = new ArrayList<>();
        if (district != null) {
            String d = hi ? culture.hindiDistrict(district) : CultureCatalog.pretty(district);
            out.add(hello + (hi ? d + " से हम दोनों का नाता है। आप " + d + " में किस जगह से हैं?"
                    : "I see we are both from " + d + ". Which part of " + d + " do you call home?"));
        } else if (division != null) {
            String d = hi ? culture.hindiDivision(division) : CultureCatalog.pretty(division);
            out.add(hello + (hi ? "हम दोनों की जड़ें " + d + " में हैं। क्या आप अक्सर घर जा पाते हैं?"
                    : "We both have roots in " + d + ". Do you get to visit home often?"));
        }
        if (language != null) {
            out.add(hello + (hi ? "हम दोनों " + language + " बोलते हैं। क्या आप घर पर भी यही भाषा बोलते हैं?"
                    : "We both speak " + language + ". Is it the language you use at home?"));
        }
        for (String fest : festivals) {
            if (out.size() >= 5) break;
            out.add(hello + (hi ? fest + " मुझे घर की याद दिलाता है। आपके परिवार में यह कैसे मनाया जाता है?"
                    : fest + " always reminds me of home. How does your family celebrate it?"));
        }
        out.add(hello + (hi ? "आपकी प्रोफ़ाइल देखी, सोचा नमस्ते कह दूँ। छुट्टी के दिन आपको क्या करना पसंद है?"
                : "I came across your profile and wanted to say hello. What do you enjoy doing on weekends?"));
        out.add(hello + (hi ? "आपके हिसाब से पहाड़ों की सबसे अच्छी बात क्या है?"
                : "What do you like most about growing up in the hills?"));
        return out;
    }
}
