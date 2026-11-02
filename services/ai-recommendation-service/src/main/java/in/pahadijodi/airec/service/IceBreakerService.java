package in.pahadijodi.airec.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.airec.domain.ProfileProjection;
import in.pahadijodi.airec.llm.*;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class IceBreakerService {
    private static final Logger log = LoggerFactory.getLogger(IceBreakerService.class);
    /** Things a first message must not push: contact details, photos, money, horoscope. */
    private static final Pattern FORBIDDEN = Pattern.compile("(?i)\\b(number|whatsapp|phone|call me|photo|pic|picture|salary|income|paise|kundli|horoscope|dowry)\\b|नंबर|फोटो|वेतन|कुंडली");

    public record Result(UUID profileId, List<String> suggestions, List<String> shared, String language, String source, boolean cached) {}

    private final ProjectionService projections;
    private final ProfileVisibility visibility;
    private final Llm llm;
    private final PromptRegistry prompts;
    private final CultureCatalog culture;
    private final Redactor redactor;
    private final ObjectMapper mapper;

    public IceBreakerService(ProjectionService projections, ProfileVisibility visibility, Llm llm, PromptRegistry prompts,
                             CultureCatalog culture, Redactor redactor, ObjectMapper mapper) {
        this.projections = projections; this.visibility = visibility; this.llm = llm; this.prompts = prompts;
        this.culture = culture; this.redactor = redactor; this.mapper = mapper;
    }

    public Result generate(UUID userId, UUID targetProfileId, String lang, String tone, int n) {
        ProfileProjection a = projections.requireByUser(userId);
        ProfileProjection b = projections.requireById(targetProfileId);
        if (a.getProfileId().equals(b.getProfileId())) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "CANNOT_MESSAGE_SELF", "Pick another profile");
        if (!visibility.canView(a, b)) throw new PjException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found");

        Map<String, Object> common = new LinkedHashMap<>();
        List<String> shared = new ArrayList<>();
        if (a.getDistrict() != null && a.getDistrict().equals(b.getDistrict())) { common.put("sameDistrict", a.getDistrict()); shared.add("DISTRICT"); }
        else if (a.getDivision() != null && a.getDivision().equals(b.getDivision())) { common.put("sameDivision", a.getDivision()); shared.add("DIVISION"); }
        if (a.getMotherTongue() != null && a.getMotherTongue().equalsIgnoreCase(b.getMotherTongue())) { common.put("sameLanguage", a.getMotherTongue()); shared.add("LANGUAGE"); }
        if (a.getCommunity() != null && a.getCommunity().equals(b.getCommunity())) shared.add("COMMUNITY");

        // festivals observed in BOTH profiles' regions (so mentioning them is grounded in what is shared)
        Set<String> aFest = new LinkedHashSet<>(), bFest = new LinkedHashSet<>();
        boolean hi = "hi".equals(lang);
        culture.festivalsFor(a.getDivision()).forEach(f -> aFest.add(hi ? f.nameHi() : f.name()));
        culture.festivalsFor(b.getDivision()).forEach(f -> bFest.add(hi ? f.nameHi() : f.name()));
        aFest.retainAll(bFest);
        if (a.getDivision() != null && b.getDivision() != null && !aFest.isEmpty()) {
            common.put("festivals", aFest.stream().limit(2).toList());
            shared.add("FESTIVALS");
        }
        common.put("tone", tone); common.put("lang", lang); common.put("n", n);

        try {
            var out = llm.chat(prompts.render("chat.icebreaker.v2", Map.of(
                    "n", Integer.toString(n),
                    "shared_interests_region_values_json", mapper.writeValueAsString(common),
                    "formal|friendly", tone), common));
            List<String> valid = validate(out.text(), n);
            if (!valid.isEmpty()) return new Result(targetProfileId, valid, shared, lang, "llm", out.cached());
        } catch (Exception e) {
            log.info("ice-breakers falling back to curated library: {}", e.toString());
        }
        List<String> curated = IceBreakerTemplates.generate(common, culture).stream().limit(n).toList();
        return new Result(targetProfileId, curated, shared, lang, "fallback", false);
    }

    private List<String> validate(String json, int n) throws Exception {
        List<String> out = new ArrayList<>();
        JsonNode arr = mapper.readTree(json).path("suggestions");
        if (!arr.isArray()) return out;
        for (JsonNode s : arr) {
            String t = s.asText("").trim();
            if (t.isBlank() || BioTemplates.wordCount(t) > 25) continue;     // max 25 words each
            if (redactor.containsPii(t) || t.matches("(?s).*\\[(EMAIL|PHONE|ID|LINK|HANDLE|NUMBER)].*") || FORBIDDEN.matcher(t).find()) continue;
            if (out.size() < n) out.add(t);
        }
        return out;
    }
}
