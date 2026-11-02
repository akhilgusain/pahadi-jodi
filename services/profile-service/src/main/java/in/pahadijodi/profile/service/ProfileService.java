package in.pahadijodi.profile.service;

import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.profile.api.ProfileDtos.ProfileRequest;
import in.pahadijodi.profile.domain.Profile;
import in.pahadijodi.profile.domain.ProfileRepository;
import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final ProfileRepository repo;
    private final OutboxService outbox;

    public ProfileService(ProfileRepository repo, OutboxService outbox) {
        this.repo = repo;
        this.outbox = outbox;
    }

    @Transactional
    public Profile upsert(UUID userId, ProfileRequest r) {
        if (Period.between(r.dateOfBirth(), LocalDate.now()).getYears() < 18) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "UNDERAGE", "Must be at least 18 years old");
        }
        boolean isNew = repo.findByUserId(userId).isEmpty();
        Profile p = repo.findByUserId(userId).orElseGet(() -> new Profile(userId));
        p.update(r.displayName(), r.gender(), r.dateOfBirth(), r.community(), r.caste(), r.subCaste(), r.gotra(),
                r.district(), r.village(), r.nativePlace(), r.motherTongue(), r.education(), r.occupation(), r.bio());
        p = repo.save(p);
        // search-service consumes this to (re)index; keep payload to non-sensitive search facets
        var doc = new java.util.LinkedHashMap<String, Object>();
        doc.put("profileId", p.getId().toString());
        doc.put("userId", userId.toString());
        doc.put("displayName", p.getDisplayName());
        doc.put("gender", p.getGender().name());
        doc.put("age", Period.between(p.getDateOfBirth(), LocalDate.now()).getYears());
        doc.put("community", p.getCommunity().name());
        doc.put("caste", p.getCaste());
        doc.put("subCaste", p.getSubCaste());
        doc.put("gotra", p.getGotra());
        doc.put("district", p.getDistrict().name());
        doc.put("division", p.getDistrict().division().name());
        doc.put("village", p.getVillage());
        doc.put("education", p.getEducation());
        doc.put("occupation", p.getOccupation());
        doc.put("motherTongue", p.getMotherTongue());
        doc.put("completeness", p.getCompleteness());
        // consumed by search-service, matchmaking-service, ai-recommendation-service, horoscope-service (facets only; no contact data)
        outbox.publish("pj.profile.updated.v1", isNew ? "ProfileCreated" : "ProfileUpdated", p.getId().toString(), doc);
        return p;
    }

    @Transactional(readOnly = true)
    public Profile mine(UUID userId) {
        return repo.findByUserId(userId).orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public Profile byId(UUID id) {
        return repo.findById(id).orElseThrow(this::notFound);
    }

    private PjException notFound() {
        return new PjException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found");
    }
}
