package in.pahadijodi.matchmaking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "partner_preference")
public class PartnerPreference {
    /** A listed dealbreaker turns the matching preference from a soft score into a hard filter. */
    public enum Dealbreaker { AGE, COMMUNITY, DISTRICT, DIVISION, MOTHER_TONGUE, EDUCATION, OCCUPATION }

    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "age_min") private Integer ageMin;
    @Column(name = "age_max") private Integer ageMax;
    private String communities;
    private String districts;
    private String division;
    private String educations;
    private String occupations;
    @Column(name = "mother_tongues") private String motherTongues;
    private String dealbreakers;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    protected PartnerPreference() {}
    public PartnerPreference(UUID userId) { this.userId = userId; }

    public void apply(Integer ageMin, Integer ageMax, List<String> communities, List<String> districts, String division,
                      List<String> educations, List<String> occupations, List<String> motherTongues,
                      Set<Dealbreaker> dealbreakers) {
        this.ageMin = ageMin; this.ageMax = ageMax; this.communities = csv(communities); this.districts = csv(districts);
        this.division = division == null || division.isBlank() ? null : division.trim().toUpperCase();
        this.educations = csv(educations); this.occupations = csv(occupations); this.motherTongues = csv(motherTongues);
        this.dealbreakers = dealbreakers == null ? null
                : dealbreakers.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        this.updatedAt = Instant.now();
    }

    private static String csv(List<String> l) {
        return l == null || l.isEmpty() ? null : l.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining(","));
    }
    private static List<String> split(String s) {
        return s == null || s.isBlank() ? List.of() : Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    public UUID getUserId() { return userId; }
    public Integer getAgeMin() { return ageMin; }
    public Integer getAgeMax() { return ageMax; }
    public String getDivision() { return division; }
    public List<String> communityList() { return split(communities); }
    public List<String> districtList() { return split(districts); }
    public List<String> educationList() { return split(educations); }
    public List<String> occupationList() { return split(occupations); }
    public List<String> motherTongueList() { return split(motherTongues); }
    public Set<Dealbreaker> dealbreakerSet() {
        return split(dealbreakers).stream().map(Dealbreaker::valueOf).collect(Collectors.toSet());
    }
    public Instant getUpdatedAt() { return updatedAt; }
}
