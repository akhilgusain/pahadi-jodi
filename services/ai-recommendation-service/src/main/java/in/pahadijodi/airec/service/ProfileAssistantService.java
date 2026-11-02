package in.pahadijodi.airec.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.airec.domain.ProfileProjection;
import in.pahadijodi.airec.llm.*;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProfileAssistantService {
    private static final Logger log = LoggerFactory.getLogger(ProfileAssistantService.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[(EMAIL|PHONE|ID|LINK|HANDLE|NUMBER)]");
    private static final Pattern CLICHE = Pattern.compile("(?i)better half|soul ?mate|tall,? dark|fair and lovely|rich family");

    public record Bio(String text, List<String> highlights) {}
    public record BioResult(List<Bio> suggestions, String language, String source, boolean cached) {}
    public record Hint(String field, String impact, String message) {}
    public record Review(int completeness, String level, List<Hint> hints) {}

    private final ProjectionService projections;
    private final Llm llm;
    private final PromptRegistry prompts;
    private final CultureCatalog culture;
    private final Redactor redactor;
    private final ObjectMapper mapper;

    public ProfileAssistantService(ProjectionService projections, Llm llm, PromptRegistry prompts, CultureCatalog culture,
                                   Redactor redactor, ObjectMapper mapper) {
        this.projections = projections; this.llm = llm; this.prompts = prompts; this.culture = culture;
        this.redactor = redactor; this.mapper = mapper;
    }

    public BioResult bioSuggestions(UUID userId, String tone, String lang, Integer maxWordsOrNull, List<String> interests, String draft) {
        ProfileProjection p = projections.requireByUser(userId);
        int maxWords = Math.max(40, Math.min(150, maxWordsOrNull == null ? 90 : maxWordsOrNull));
        List<String> cleanInterests = interests == null ? List.of() : interests.stream().filter(s -> s != null && !s.isBlank() && !redactor.containsPii(s))
                .map(s -> s.trim().length() > 30 ? s.trim().substring(0, 30) : s.trim()).distinct().limit(5).toList();

        // facts: structured only. No display name, no ids, no contact data.
        Map<String, Object> facts = new LinkedHashMap<>();
        put(facts, "district", p.getDistrict()); put(facts, "division", p.getDivision()); put(facts, "village", p.getVillage());
        put(facts, "community", p.getCommunity()); put(facts, "education", p.getEducation()); put(facts, "occupation", p.getOccupation());
        put(facts, "language", p.getMotherTongue()); put(facts, "age", p.getAge() > 0 ? p.getAge() : null);
        facts.put("interests", cleanInterests);
        Map<String, Object> promptFacts = new LinkedHashMap<>(facts);
        facts.put("tone", tone); facts.put("maxWords", maxWords); facts.put("lang", lang);

        try {
            var out = llm.chat(prompts.render("assistant.bio.v2", Map.of(
                    "max_words", Integer.toString(maxWords), "tone", tone, "language", "hi".equals(lang) ? "Hindi" : "English",
                    "structured_facts_json", mapper.writeValueAsString(promptFacts),
                    "draft_or_empty", draft == null ? "" : draft.length() > 1000 ? draft.substring(0, 1000) : draft), facts));
            List<Bio> valid = validate(out.text(), maxWords);
            if (valid.size() == 3) return new BioResult(valid, lang, "llm", out.cached());
            log.info("bio output failed guardrails ({} valid of 3); using templates", valid.size());
        } catch (Exception e) {
            log.info("bio generation falling back to templates: {}", e.toString());
        }
        List<Bio> fallback = BioTemplates.generate(facts, culture).stream()
                .filter(t -> BioTemplates.wordCount(t) <= maxWords).map(t -> new Bio(t, List.of())).toList();
        return new BioResult(fallback, lang, "fallback", false);
    }

    private List<Bio> validate(String json, int maxWords) throws Exception {
        List<Bio> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonNode arr = mapper.readTree(json).path("suggestions");
        if (!arr.isArray()) return out;
        for (JsonNode s : arr) {
            String text = s.path("text").asText("").trim();
            if (text.isBlank() || BioTemplates.wordCount(text) > maxWords) continue;     // output length guardrail
            if (redactor.containsPii(text) || PLACEHOLDER.matcher(text).find() || CLICHE.matcher(text).find()) continue;   // no contact details, redaction tags or clichés
            if (!seen.add(text.toLowerCase())) continue;                                  // variants must be distinct
            List<String> hl = new ArrayList<>();
            s.path("highlights").forEach(h -> { if (hl.size() < 4) hl.add(h.asText()); });
            out.add(new Bio(text, hl));
        }
        return out;
    }

    /** Pure rules (no LLM): what would make this profile stronger. */
    public Review review(UUID userId) {
        ProfileProjection p = projections.requireByUser(userId);
        List<Hint> hints = new ArrayList<>();
        int known = 40;   // mandatory basics, mirrors profile-service's completeness weights
        if (blank(p.getVillage())) hints.add(new Hint("village", "HIGH", "Add your native village. Many Pahadi families look for it first.")); else known += 10;
        if (blank(p.getEducation())) hints.add(new Hint("education", "HIGH", "Add your education.")); else known += 8;
        if (blank(p.getOccupation())) hints.add(new Hint("occupation", "MEDIUM", "Add what you do for work.")); else known += 7;
        if (blank(p.getGotra())) hints.add(new Hint("gotra", "MEDIUM", "Add your gotra; families usually check it early.")); else known += 5;
        if (blank(p.getCaste())) hints.add(new Hint("caste", "LOW", "Add your caste if you want it considered in matching.")); else known += 5;
        if (!blank(p.getSubCaste())) known += 5;
        if (blank(p.getMotherTongue())) hints.add(new Hint("motherTongue", "MEDIUM", "Mention your mother tongue (Garhwali, Kumaoni, Jaunsari, Hindi...)."));
        if (p.getCompleteness() - known < 20) {   // event does not carry nativePlace/bio: infer from the score gap
            hints.add(new Hint("bio", "HIGH", "Write an About-me of at least 100 characters. The bio assistant can draft three options."));
            hints.add(new Hint("nativePlace", "LOW", "Add your native place if it differs from where you live now."));
        }
        String level = p.getCompleteness() >= 85 ? "STRONG" : p.getCompleteness() >= 65 ? "GOOD" : "NEEDS_WORK";
        return new Review(p.getCompleteness(), level, hints);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static void put(Map<String, Object> m, String k, Object v) { if (v != null) m.put(k, v); }
}
