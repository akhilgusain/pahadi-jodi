package in.pahadijodi.airec.service;

import com.fasterxml.jackson.databind.JsonNode;
import in.pahadijodi.airec.domain.ProfileProjection;
import in.pahadijodi.airec.domain.ProfileProjectionRepository;
import in.pahadijodi.airec.llm.Llm;
import in.pahadijodi.airec.llm.LlmUnavailableException;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintains the local profile read model from pj.profile.updated.v1 and (re)computes embeddings only when the features change. */
@Service
public class ProjectionService {
    private static final Logger log = LoggerFactory.getLogger(ProjectionService.class);

    private final ProfileProjectionRepository repo;
    private final Llm llm;

    public ProjectionService(ProfileProjectionRepository repo, Llm llm) { this.repo = repo; this.llm = llm; }

    @Transactional
    public boolean apply(JsonNode p) {
        UUID profileId = uuid(p.path("profileId").asText(null));
        UUID userId = uuid(p.path("userId").asText(null));
        if (profileId == null || userId == null || p.path("gender").asText("").isBlank()) {
            log.warn("profile event skipped: missing profileId/userId/gender");
            return false;
        }
        ProfileProjection pp = repo.findById(profileId).orElseGet(() -> new ProfileProjection(profileId, userId));
        pp.apply(text(p, "displayName"), p.path("gender").asText(), p.path("age").asInt(0), text(p, "community"), text(p, "caste"),
                text(p, "subCaste"), text(p, "gotra"), text(p, "district"), text(p, "division"), text(p, "village"),
                text(p, "education"), text(p, "occupation"), text(p, "motherTongue"), p.path("completeness").asInt(0));

        String featureText = embeddingText(pp);
        String hash = sha256(featureText + "|" + llm.embeddingVersion());
        if (!hash.equals(pp.getTextHash())) {                       // re-embed only when the features changed
            try {
                pp.setEmbedding(format(llm.embed(featureText)), llm.embeddingVersion(), hash);
            } catch (LlmUnavailableException e) {
                pp.setEmbedding(null, null, null);                  // retried on the next profile update
                log.warn("embedding failed for profile {}; will retry on next update", profileId);
            }
        }
        repo.save(pp);
        return true;
    }

    @Transactional(readOnly = true)
    public ProfileProjection requireByUser(UUID userId) {
        return repo.findByUserId(userId).orElseThrow(() ->
                new PjException(HttpStatus.NOT_FOUND, "PROFILE_REQUIRED", "Create your profile first to use AI features"));
    }

    @Transactional(readOnly = true)
    public ProfileProjection requireById(UUID profileId) {
        return repo.findById(profileId).orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found"));
    }

    /** Facet tokens only (no names or contact data). Gender is deliberately excluded; opposite-gender filtering is explicit. */
    public static String embeddingText(ProfileProjection p) {
        StringBuilder sb = new StringBuilder();
        add(sb, "community", p.getCommunity());
        add(sb, "district", p.getDistrict());
        add(sb, "division", p.getDivision());
        add(sb, "lang", p.getMotherTongue());
        add(sb, "edu", EducationLevel.of(p.getEducation()).name());
        add(sb, "occ", p.getOccupation());
        add(sb, "village", p.getVillage());
        if (p.getAge() > 0) add(sb, "age", (p.getAge() / 5 * 5) + "-" + (p.getAge() / 5 * 5 + 4));
        return sb.toString().trim();
    }

    private static void add(StringBuilder sb, String key, String value) {
        if (value == null || value.isBlank()) return;
        sb.append(key).append(':').append(value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_")).append(' ');
    }

    public static String format(float[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) sb.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.5f", v[i]));
        return sb.toString();
    }

    public static float[] parse(String s) {
        String[] parts = s.split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < v.length; i++) v[i] = Float.parseFloat(parts[i]);
        return v;
    }

    private static String text(JsonNode n, String f) {
        String v = n.path(f).asText(null);
        return v == null || v.isBlank() || "null".equals(v) ? null : v;
    }

    private static UUID uuid(String s) {
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
