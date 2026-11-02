package in.pahadijodi.airec.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile_projection")
public class ProfileProjection {
    @Id @Column(name = "profile_id") private UUID profileId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "display_name") private String displayName;
    @Column(nullable = false) private String gender;
    @Column(nullable = false) private int age;
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
    private String embedding;
    @Column(name = "embedding_version") private String embeddingVersion;
    @Column(name = "text_hash") private String textHash;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ProfileProjection() {}
    public ProfileProjection(UUID profileId, UUID userId) { this.profileId = profileId; this.userId = userId; }

    public void apply(String displayName, String gender, int age, String community, String caste, String subCaste, String gotra,
                      String district, String division, String village, String education, String occupation,
                      String motherTongue, int completeness) {
        this.displayName = displayName; this.gender = gender; this.age = age; this.community = community; this.caste = caste;
        this.subCaste = subCaste; this.gotra = gotra; this.district = district; this.division = division; this.village = village;
        this.education = education; this.occupation = occupation; this.motherTongue = motherTongue;
        this.completeness = completeness; this.updatedAt = Instant.now();
    }

    public void setEmbedding(String embedding, String version, String textHash) {
        this.embedding = embedding; this.embeddingVersion = version; this.textHash = textHash;
    }

    public UUID getProfileId() { return profileId; }
    public UUID getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getGender() { return gender; }
    public int getAge() { return age; }
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
    public String getEmbedding() { return embedding; }
    public String getEmbeddingVersion() { return embeddingVersion; }
    public String getTextHash() { return textHash; }
}
