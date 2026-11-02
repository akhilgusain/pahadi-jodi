package in.pahadijodi.airec.llm;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Small curated reference data: Hindi names for districts/divisions and the festivals that families in each region observe. */
@Component
public class CultureCatalog {
    public record Festival(String name, String nameHi, Set<String> divisions, String blurb, String blurbHi) {}

    private static final Map<String, String> DISTRICT_HI = Map.ofEntries(
            Map.entry("DEHRADUN", "देहरादून"), Map.entry("HARIDWAR", "हरिद्वार"), Map.entry("PAURI_GARHWAL", "पौड़ी गढ़वाल"),
            Map.entry("TEHRI_GARHWAL", "टिहरी गढ़वाल"), Map.entry("CHAMOLI", "चमोली"), Map.entry("RUDRAPRAYAG", "रुद्रप्रयाग"),
            Map.entry("UTTARKASHI", "उत्तरकाशी"), Map.entry("ALMORA", "अल्मोड़ा"), Map.entry("NAINITAL", "नैनीताल"),
            Map.entry("BAGESHWAR", "बागेश्वर"), Map.entry("PITHORAGARH", "पिथौरागढ़"), Map.entry("CHAMPAWAT", "चंपावत"),
            Map.entry("UDHAM_SINGH_NAGAR", "उधम सिंह नगर"));

    private static final List<Festival> FESTIVALS = List.of(
            new Festival("Harela", "हरेला", Set.of("GARHWAL", "KUMAON"),
                    "Harela, the festival of greenery at the start of the sowing season", "हरेला, बुवाई के मौसम की हरियाली का पर्व"),
            new Festival("Phool Dei", "फूलदेई", Set.of("GARHWAL", "KUMAON"),
                    "Phool Dei, the spring festival when children place flowers on doorsteps", "फूलदेई, वसंत का पर्व जब बच्चे देहरी पर फूल सजाते हैं"),
            new Festival("Bikhauti", "बिखौती", Set.of("KUMAON"),
                    "Bikhauti (Vishuwat Sankranti) in mid-April", "बिखौती (विषुवत संक्रांति), अप्रैल के मध्य का पर्व"),
            new Festival("Uttarayani", "उत्तरायणी (घुघुतिया)", Set.of("KUMAON"),
                    "Uttarayani (Ghughuti) on Makar Sankranti, known for the Bageshwar fair", "मकर संक्रांति पर उत्तरायणी (घुघुतिया), बागेश्वर के मेले के लिए प्रसिद्ध"),
            new Festival("Nanda Devi", "नंदा देवी", Set.of("GARHWAL", "KUMAON"),
                    "the Nanda Devi fairs and the Nanda Devi Raj Jat pilgrimage", "नंदा देवी के मेले और नंदा देवी राजजात यात्रा"),
            new Festival("Egas", "इगास (बग्वाल)", Set.of("GARHWAL"),
                    "Egas (Igas), the Garhwali Diwali observed eleven days after the main Diwali", "इगास (बग्वाल), मुख्य दीपावली के ग्यारह दिन बाद मनाई जाने वाली गढ़वाली दीपावली"));

    public String hindiDistrict(String district) { return DISTRICT_HI.getOrDefault(district, district); }

    public String hindiDivision(String division) { return "KUMAON".equals(division) ? "कुमाऊँ" : "GARHWAL".equals(division) ? "गढ़वाल" : division; }

    public List<Festival> festivalsFor(String division) {
        if (division == null) return FESTIVALS;
        return FESTIVALS.stream().filter(f -> f.divisions().contains(division)).toList();
    }

    public static String pretty(String enumName) {
        if (enumName == null) return null;
        String[] parts = enumName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return sb.toString().trim();
    }
}
