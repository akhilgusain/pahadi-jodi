package in.pahadijodi.search.service;

import static in.pahadijodi.search.service.Text.norm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Seeded Uttarakhand village suggester (docs/02 section 7.2). Illustrative data: verify against LGD/Census
 * before launch. A production deployment serves this from the ES completion suggester / ref_village table.
 */
@Component
public class VillageCatalog {
    public record Village(String name, String nameHi, String district, String division, List<String> alt) {}

    private static final List<Village> VILLAGES = new ArrayList<>();

    private static void add(String district, String division, String... names) {
        for (String n : names) VILLAGES.add(new Village(n, null, district, division, List.of()));
    }

    static {
        VILLAGES.add(new Village("Kandakhal", "कांडाखाल", "PAURI_GARHWAL", "GARHWAL", List.of("Kandakhaal")));
        VILLAGES.add(new Village("Khirsu", "खिर्सू", "PAURI_GARHWAL", "GARHWAL", List.of("Khirshu")));
        VILLAGES.add(new Village("Jageshwar", "जागेश्वर", "ALMORA", "KUMAON", List.of("Jageswar")));
        VILLAGES.add(new Village("Mana", "माणा", "CHAMOLI", "GARHWAL", List.of("Maana")));
        add("DEHRADUN", "GARHWAL", "Raipur", "Sahaspur", "Selaqui", "Dakpathar", "Lakhamandal", "Kalsi");
        add("HARIDWAR", "GARHWAL", "Jwalapur", "Bahadrabad", "Manglaur");
        add("PAURI_GARHWAL", "GARHWAL", "Bironkhal", "Paithani", "Dwarikhal", "Chaundkot", "Kaljikhal");
        add("TEHRI_GARHWAL", "GARHWAL", "Chamba", "Kirtinagar", "Bhilangna", "Lambgaon", "Dhanaulti");
        add("CHAMOLI", "GARHWAL", "Niti", "Gwaldam", "Ghat", "Simli", "Nandprayag");
        add("RUDRAPRAYAG", "GARHWAL", "Guptkashi", "Sari", "Chopta", "Tilwara", "Bhiri");
        add("UTTARKASHI", "GARHWAL", "Harsil", "Dharali", "Gangotri", "Naugaon", "Netala");
        add("ALMORA", "KUMAON", "Kasar Devi", "Syalde", "Majkhali");
        add("NAINITAL", "KUMAON", "Mukteshwar", "Ramgarh", "Satkhol", "Okhalkanda", "Bhowali");
        add("BAGESHWAR", "KUMAON", "Kausani", "Baijnath", "Loharkhet", "Song");
        add("PITHORAGARH", "KUMAON", "Munsiyari", "Bhatkot", "Jauljibi", "Askot");
        add("CHAMPAWAT", "KUMAON", "Mayawati", "Barakot", "Banbasa", "Devidhura");
        add("UDHAM_SINGH_NAGAR", "KUMAON", "Pantnagar", "Gadarpur", "Sultanpur Patti");
    }

    /** Prefix match on English name, alternate spellings and Hindi name; exact-prefix on name ranks first. */
    public List<Village> suggest(String q, int limit) {
        String n = norm(q);
        if (n.isEmpty()) return List.of();
        return VILLAGES.stream()
                .filter(v -> norm(v.name()).startsWith(n)
                        || v.alt().stream().anyMatch(a -> norm(a).startsWith(n))
                        || (v.nameHi() != null && v.nameHi().startsWith(q.trim())))
                .sorted(Comparator.comparing((Village v) -> !norm(v.name()).startsWith(n)).thenComparing(Village::name))
                .limit(limit).toList();
    }
}
