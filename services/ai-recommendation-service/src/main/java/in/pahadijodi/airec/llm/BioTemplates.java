package in.pahadijodi.airec.llm;

import static in.pahadijodi.airec.llm.Facts.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deterministic "About me" variants built only from supplied facts. Used by the fake LLM and as the fallback when the LLM fails. */
public final class BioTemplates {
    private BioTemplates() {}

    public static List<String> generate(Map<String, Object> f, CultureCatalog culture) {
        boolean hi = hi(f);
        String district = str(f, "district");
        String place = str(f, "village");
        String occupation = str(f, "occupation");
        String education = str(f, "education");
        String language = str(f, "language");
        List<String> interests = list(f, "interests");
        String division = str(f, "division");
        String districtName = district == null ? null : hi ? culture.hindiDistrict(district) : CultureCatalog.pretty(district);
        String divisionName = division == null ? null : hi ? culture.hindiDivision(division) : CultureCatalog.pretty(division);
        String interestText = String.join(", ", interests);

        List<String> out = new ArrayList<>();
        if (hi) {
            out.add(join(
                    districtName == null ? "मैं उत्तराखंड से हूँ।" : "मैं उत्तराखंड के " + districtName + " से हूँ।",
                    place == null ? null : "मेरा गाँव " + place + " है।",
                    occupation == null ? null : "मैं " + occupation + " के रूप में कार्यरत हूँ।",
                    education == null ? null : "मैंने " + education + " की पढ़ाई की है।"));
            out.add(join(
                    divisionName == null ? "पहाड़ों से मेरा गहरा रिश्ता है।" : "मैं " + divisionName + " की मिट्टी से जुड़ा/जुड़ी हूँ।",
                    language == null ? null : "मेरी मातृभाषा " + language + " है।",
                    interests.isEmpty() ? null : "मुझे " + interestText + " पसंद है।"));
            out.add(join("उत्तराखंड मेरी पहचान है, और मैं अपने जीवन के अगले कदम के लिए एक साथी की तलाश में हूँ।",
                    occupation == null ? null : "काम से मैं " + occupation + " हूँ।"));
        } else {
            out.add(join(
                    districtName == null ? "I come from Uttarakhand." : "I come from " + districtName + " in Uttarakhand.",
                    place == null ? null : "My village is " + place + ".",
                    occupation == null ? null : "I work as " + occupation + ".",
                    education == null ? null : "I studied " + education + "."));
            out.add(join(
                    divisionName == null ? "I stay close to my Pahadi roots." : "I am rooted in the " + divisionName + " region of Uttarakhand.",
                    language == null ? null : "My mother tongue is " + language + ".",
                    interests.isEmpty() ? null : "I enjoy " + interestText + "."));
            out.add(join("Uttarakhand is part of who I am, and I am looking for a partner to share the next chapter of life.",
                    occupation == null ? null : "By profession I am a " + occupation + ".",
                    interests.isEmpty() ? null : "In my free time: " + interestText + "."));
        }
        return out;
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) if (p != null) sb.append(sb.length() == 0 ? "" : " ").append(p);
        return sb.toString();
    }

    public static int wordCount(String s) { return s.isBlank() ? 0 : s.trim().split("\\s+").length; }
}
