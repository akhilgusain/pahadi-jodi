package in.pahadijodi.matchmaking.service;

import in.pahadijodi.matchmaking.domain.PartnerPreference;
import in.pahadijodi.matchmaking.domain.PartnerPreference.Dealbreaker;
import in.pahadijodi.matchmaking.domain.Repositories.PartnerPreferenceRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {
    public record PreferenceDto(Integer ageMin, Integer ageMax, List<String> communities, List<String> districts, String division,
                                List<String> educations, List<String> occupations, List<String> motherTongues,
                                Set<Dealbreaker> dealbreakers) {
        static PreferenceDto of(PartnerPreference p) {
            return new PreferenceDto(p.getAgeMin(), p.getAgeMax(), p.communityList(), p.districtList(), p.getDivision(),
                    p.educationList(), p.occupationList(), p.motherTongueList(), p.dealbreakerSet());
        }
    }

    private final PartnerPreferenceRepository repo;

    public PreferenceService(PartnerPreferenceRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public PreferenceDto get(UUID userId) {
        return repo.findById(userId).map(PreferenceDto::of)
                .orElse(new PreferenceDto(null, null, List.of(), List.of(), null, List.of(), List.of(), List.of(), Set.of()));
    }

    @Transactional
    public PreferenceDto put(UUID userId, PreferenceDto d) {
        PartnerPreference p = repo.findById(userId).orElseGet(() -> new PartnerPreference(userId));
        p.apply(d.ageMin(), d.ageMax(), d.communities(), d.districts(), d.division(), d.educations(), d.occupations(),
                d.motherTongues(), d.dealbreakers());
        return PreferenceDto.of(repo.save(p));
    }
}
