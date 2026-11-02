package in.pahadijodi.airec.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pahadijodi.airec.domain.ProfileProjection;
import in.pahadijodi.airec.llm.*;
import in.pahadijodi.airec.llm.CompatNarrative.Narrative;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Deterministic numeric breakdown (never LLM) + a grounded LLM explanation, validated, with a templated fallback. */
@Service
public class CompatibilityService {
    private static final Logger log = LoggerFactory.getLogger(CompatibilityService.class);
    private static final Pattern BANNED = Pattern.compile("(?i)guarantee|perfect match|will (definitely )?(succeed|fail|work)|destined|soulmate|doomed");

    public record Explanation(UUID profileId, int overall, Map<String, Integer> breakdown, String summary, List<String> strengths,
                              List<String> considerations, String communicationStyle, String disclaimer, String language,
                              String source, boolean cached) {}

    private final ProjectionService projections;
    private final ProfileVisibility visibility;
    private final Llm llm;
    private final PromptRegistry prompts;
    private final CultureCatalog culture;
    private final Redactor redactor;
    private final ObjectMapper mapper;

    public CompatibilityService(ProjectionService projections, ProfileVisibility visibility, Llm llm, PromptRegistry prompts,
                                CultureCatalog culture, Redactor redactor, ObjectMapper mapper) {
        this.projections = projections; this.visibility = visibility; this.llm = llm; this.prompts = prompts;
        this.culture = culture; this.redactor = redactor; this.mapper = mapper;
    }

    public Explanation explain(UUID requesterUserId, UUID targetProfileId, String lang) {
        ProfileProjection a = projections.requireByUser(requesterUserId);
        ProfileProjection b = projections.requireById(targetProfileId);
        if (a.getProfileId().equals(b.getProfileId())) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "CANNOT_COMPARE_SELF", "Pick another profile to compare with");
        }
        if (!visibility.canView(a, b)) throw new PjException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found");

        Map<String, Integer> scores = score(a, b);
        int overall = scores.get("overall");
        Map<String, Object> pa = facts(a), pb = facts(b);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("a", pa); facts.put("b", pb); facts.put("overall", overall); facts.put("lang", lang);

        String source = "llm";
        boolean cached = false;
        Narrative n;
        try {
            var out = llm.chat(prompts.render("compat.explain.v3", Map.of(
                    "language", lang,
                    "score_breakdown_json", mapper.writeValueAsString(scores),
                    "redacted_profile_a", mapper.writeValueAsString(pa),
                    "redacted_profile_b", mapper.writeValueAsString(pb)), facts));
            cached = out.cached();
            n = parseAndValidate(out.text());
        } catch (Exception e) {      // LlmUnavailable, bad JSON, or guardrail rejection
            log.info("compat explanation falling back to template: {}", e.toString());
            n = CompatNarrative.build(facts, culture);
            source = "fallback";
        }
        return new Explanation(targetProfileId, overall, scores, n.summary(), n.strengths(), n.discussionPoints(),
                n.communicationStyle(), n.disclaimer(), lang, source, cached);
    }

    private Narrative parseAndValidate(String json) throws Exception {
        JsonNode j = mapper.readTree(json);
        String summary = j.path("summary").asText("");
        if (summary.isBlank() || BioTemplates.wordCount(summary) > 150) throw new IllegalArgumentException("summary missing or too long");
        List<String> strengths = strings(j.path("strengths"), 3);
        List<String> points = strings(j.path("discussionPoints"), 3);
        String style = j.path("communicationStyle").asText("");
        String disclaimer = j.path("disclaimer").asText("");
        if (disclaimer.isBlank()) throw new IllegalArgumentException("disclaimer missing");
        String all = summary + " " + strengths + " " + points + " " + style;
        if (BANNED.matcher(all).find()) throw new IllegalArgumentException("guardrail: promise/prediction language");
        if (redactor.containsPii(all)) throw new IllegalArgumentException("guardrail: PII in output");
        return new Narrative(summary, strengths, points, style, disclaimer);
    }

    private static List<String> strings(JsonNode arr, int max) {
        List<String> out = new ArrayList<>();
        if (arr.isArray()) arr.forEach(x -> { if (out.size() < max && !x.asText("").isBlank()) out.add(x.asText()); });
        return out;
    }

    /** Weighted, explainable score. Caste is deliberately NOT a factor. Weights: region 30, community 20, language 20, education 15, age 15. */
    public static Map<String, Integer> score(ProfileProjection a, ProfileProjection b) {
        double region = a.getDistrict() != null && a.getDistrict().equals(b.getDistrict()) ? 1.0
                : a.getDivision() != null && a.getDivision().equals(b.getDivision()) ? 0.7
                : a.getDivision() == null || b.getDivision() == null ? 0.5 : 0.3;
        double community = a.getCommunity() == null || b.getCommunity() == null ? 0.5 : a.getCommunity().equals(b.getCommunity()) ? 1.0 : 0.4;
        double language = a.getMotherTongue() == null || b.getMotherTongue() == null ? 0.5 : a.getMotherTongue().equalsIgnoreCase(b.getMotherTongue()) ? 1.0 : 0.5;
        var ea = EducationLevel.of(a.getEducation());
        var eb = EducationLevel.of(b.getEducation());
        int gap = Math.abs(ea.ordinal() - eb.ordinal());
        double education = ea == EducationLevel.UNKNOWN || eb == EducationLevel.UNKNOWN ? 0.5 : gap == 0 ? 1.0 : gap == 1 ? 0.8 : 0.5;
        int ageGap = Math.abs(a.getAge() - b.getAge());
        double age = a.getAge() <= 0 || b.getAge() <= 0 ? 0.5 : ageGap <= 3 ? 1.0 : ageGap <= 5 ? 0.85 : ageGap <= 8 ? 0.6 : 0.3;
        double total = 0.30 * region + 0.20 * community + 0.20 * language + 0.15 * education + 0.15 * age;
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("region", pct(region)); m.put("community", pct(community)); m.put("language", pct(language));
        m.put("education", pct(education)); m.put("age", pct(age)); m.put("overall", pct(total));
        return m;
    }

    private static int pct(double d) { return (int) Math.round(d * 100); }

    /** What the model (and the fake) may see: facets only. No name, no ids, no caste. */
    static Map<String, Object> facts(ProfileProjection p) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "age", p.getAge() > 0 ? p.getAge() : null); put(m, "community", p.getCommunity()); put(m, "district", p.getDistrict());
        put(m, "division", p.getDivision()); put(m, "language", p.getMotherTongue()); put(m, "education", p.getEducation());
        put(m, "occupation", p.getOccupation()); put(m, "gotra", p.getGotra());
        return m;
    }

    private static void put(Map<String, Object> m, String k, Object v) { if (v != null) m.put(k, v); }
}
