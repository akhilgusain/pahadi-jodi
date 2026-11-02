package in.pahadijodi.matchmaking.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Read-only local copy of the facets needed for scoring. Written only by the profile event handler. */
@Entity
@Table(name = "profile_facet")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileFacet {
    @Id @Column(name = "profile_id") private UUID profileId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "display_name") private String displayName;
    private String gender;
    private Integer age;
    private String community;
    private String caste;
    @Column(name = "sub_caste") private String subCaste;
    private String gotra;
    private String district;
    private String division;
    private String village;
    private String education;
    private String occupation;
    @Column(name = "mother_tongue") private String motherTongue;
    @Column(nullable = false) private int completeness;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    protected ProfileFacet() {}

    public ProfileFacet(UUID profileId, UUID userId, String displayName, String gender, Integer age, String community,
                        String caste, String subCaste, String gotra, String district, String division, String village,
                        String education, String occupation, String motherTongue, int completeness) {
        this.profileId = profileId; this.userId = userId; this.displayName = displayName; this.gender = gender;
        this.age = age; this.community = community; this.caste = caste; this.subCaste = subCaste; this.gotra = gotra;
        this.district = district; this.division = division; this.village = village; this.education = education;
        this.occupation = occupation; this.motherTongue = motherTongue; this.completeness = completeness;
    }

    public UUID getProfileId() { return profileId; }
    public UUID getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getGender() { return gender; }
    public Integer getAge() { return age; }
    public String getCommunity() { return community; }
    public String getCaste() { return caste; }
    public String getSubCaste() { return subCaste; }
    public String getGotra() { return gotra; }
    public String getDistrict() { return district; }
    public String getDivision() { return division; }
    public String getVillage() { return village; }
    public String getEducation() { return education; }
    public String getOccupation() { return occupation; }
    public String getMotherTongue() { return motherTongue; }
    public int getCompleteness() { return completeness; }
}
