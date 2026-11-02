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
        outbox.publish("pj.profile.updated.v1", isNew ? "ProfileCreated" : "ProfileUpdated", p.getId().toString(),
                Map.of("profileId", p.getId().toString(), "userId", userId.toString(),
                        "community", p.getCommunity().name(), "district", p.getDistrict().name(),
                        "division", p.getDistrict().division().name(), "completeness", p.getCompleteness()));
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
