package in.pahadijodi.airec.service;

import in.pahadijodi.airec.domain.ProfileProjection;
import in.pahadijodi.airec.domain.ProfileProjectionRepository;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Embedding similarity over the local projection. At scale replace the in-memory scan with an Elasticsearch kNN query (docs/03 section 11). */
@Service
public class SimilarityService {
    public record Similar(UUID profileId, String displayName, int age, String district, String community, double similarity, List<String> reasons) {}

    private final ProfileProjectionRepository repo;
    private final ProjectionService projections;
    private final ProfileVisibility visibility;

    public SimilarityService(ProfileProjectionRepository repo, ProjectionService projections, ProfileVisibility visibility) {
        this.repo = repo; this.projections = projections; this.visibility = visibility;
    }

    /**
     * @param baseProfileId profile to find lookalikes of; null = the requester's own profile
     * @param gender        MALE/FEMALE/OTHER to restrict, ANY for everyone, null = opposite of the base profile (MALE/FEMALE) else any
     */
    @Transactional(readOnly = true)
    public List<Similar> similar(UUID requesterUserId, UUID baseProfileId, int limit, String gender) {
        ProfileProjection me = projections.requireByUser(requesterUserId);
        ProfileProjection base = baseProfileId == null ? me : projections.requireById(baseProfileId);
        if (base.getEmbedding() == null) {
            throw new PjException(HttpStatus.CONFLICT, "EMBEDDING_NOT_READY", "Profile is still being analysed; try again shortly");
        }
        String wanted = gender != null ? gender.toUpperCase() : "MALE".equals(base.getGender()) ? "FEMALE" : "FEMALE".equals(base.getGender()) ? "MALE" : "ANY";
        float[] b = ProjectionService.parse(base.getEmbedding());
        List<Similar> out = new ArrayList<>();
        for (ProfileProjection c : repo.findByEmbeddingVersion(base.getEmbeddingVersion())) {
            if (Objects.equals(c.getUserId(), requesterUserId) || c.getProfileId().equals(base.getProfileId())) continue;
            if (!"ANY".equals(wanted) && !wanted.equals(c.getGender())) continue;
            if (!visibility.canView(me, c)) continue;
            double sim = Math.max(0, cosine(b, ProjectionService.parse(c.getEmbedding())));
            out.add(new Similar(c.getProfileId(), c.getDisplayName(), c.getAge(), c.getDistrict(), c.getCommunity(),
                    Math.round(sim * 1000) / 1000.0, reasons(base, c)));
        }
        out.sort(Comparator.comparingDouble(Similar::similarity).reversed().thenComparing(Similar::profileId));
        return out.stream().limit(limit).toList();
    }

    static double cosine(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** Reason codes rendered as short phrases (never LLM text, so they are always grounded). */
    static List<String> reasons(ProfileProjection a, ProfileProjection b) {
        List<String> r = new ArrayList<>();
        if (a.getDistrict() != null && a.getDistrict().equals(b.getDistrict())) r.add("SAME_DISTRICT");
        else if (a.getDivision() != null && a.getDivision().equals(b.getDivision())) r.add("SAME_DIVISION");
        if (a.getCommunity() != null && a.getCommunity().equals(b.getCommunity())) r.add("SAME_COMMUNITY");
        if (a.getMotherTongue() != null && a.getMotherTongue().equalsIgnoreCase(b.getMotherTongue())) r.add("SAME_LANGUAGE");
        var ea = EducationLevel.of(a.getEducation());
        if (ea != EducationLevel.UNKNOWN && ea == EducationLevel.of(b.getEducation())) r.add("SIMILAR_EDUCATION");
        if (a.getAge() > 0 && b.getAge() > 0 && Math.abs(a.getAge() - b.getAge()) <= 5) r.add("CLOSE_AGE");
        return r;
    }
}
