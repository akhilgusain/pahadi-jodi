package in.pahadijodi.profile.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "profile")
public class Profile {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false, unique = true) private UUID userId;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Gender gender;
    @Column(name = "date_of_birth", nullable = false) private LocalDate dateOfBirth;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Community community;
    private String caste;
    @Column(name = "sub_caste") private String subCaste;
    private String gotra;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private District district;
    private String village;
    @Column(name = "native_place") private String nativePlace;
    @Column(name = "mother_tongue") private String motherTongue;
    private String education;
    private String occupation;
    private String bio;
    @Column(nullable = false) private int completeness;
    @Version private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Profile() {}

    public Profile(UUID userId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    /** Domain behaviour: applies an edit and recomputes the completeness score. */
    public void update(String displayName, Gender gender, LocalDate dob, Community community, String caste,
                       String subCaste, String gotra, District district, String village, String nativePlace,
                       String motherTongue, String education, String occupation, String bio) {
        this.displayName = displayName; this.gender = gender; this.dateOfBirth = dob;
        this.community = community; this.caste = caste; this.subCaste = subCaste; this.gotra = gotra;
        this.district = district; this.village = village; this.nativePlace = nativePlace;
        this.motherTongue = motherTongue; this.education = education; this.occupation = occupation;
        this.bio = bio;
        this.updatedAt = Instant.now();
        this.completeness = computeCompleteness();
    }

    /** Weighted: mandatory basics 40, heritage 30, career 15, bio 15. Tune in docs/02. */
    int computeCompleteness() {
        int s = 40; // displayName, gender, dob, community, district are mandatory
        if (has(caste)) s += 5;
        if (has(subCaste)) s += 5;
        if (has(gotra)) s += 5;
        if (has(village)) s += 10;
        if (has(nativePlace)) s += 5;
        if (has(education)) s += 8;
        if (has(occupation)) s += 7;
        if (bio != null && bio.length() >= 100) s += 15; else if (has(bio)) s += 7;
        return Math.min(s, 100);
    }
    private static boolean has(String v) { return v != null && !v.isBlank(); }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public Gender getGender() { return gender; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public Community getCommunity() { return community; }
    public String getCaste() { return caste; }
    public String getSubCaste() { return subCaste; }
    public String getGotra() { return gotra; }
    public District getDistrict() { return district; }
    public String getVillage() { return village; }
    public String getNativePlace() { return nativePlace; }
    public String getMotherTongue() { return motherTongue; }
    public String getEducation() { return education; }
    public String getOccupation() { return occupation; }
    public String getBio() { return bio; }
    public int getCompleteness() { return completeness; }
}
