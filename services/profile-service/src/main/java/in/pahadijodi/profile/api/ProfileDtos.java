package in.pahadijodi.profile.api;

import in.pahadijodi.profile.domain.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record ProfileRequest(
            @NotBlank @Size(max = 100) String displayName,
            @NotNull Gender gender,
            @NotNull @Past LocalDate dateOfBirth,
            @NotNull Community community,
            @Size(max = 80) String caste,
            @Size(max = 80) String subCaste,
            @Size(max = 80) String gotra,
            @NotNull District district,
            @Size(max = 120) String village,
            @Size(max = 160) String nativePlace,
            @Size(max = 30) String motherTongue,
            @Size(max = 120) String education,
            @Size(max = 120) String occupation,
            @Size(max = 2000) String bio) {}

    public record ProfileResponse(UUID id, String displayName, Gender gender, LocalDate dateOfBirth,
                                  Community community, String caste, String subCaste, String gotra,
                                  District district, String division, String village, String nativePlace,
                                  String motherTongue, String education, String occupation, String bio,
                                  int completeness) {
        public static ProfileResponse from(Profile p) {
            return new ProfileResponse(p.getId(), p.getDisplayName(), p.getGender(), p.getDateOfBirth(),
                    p.getCommunity(), p.getCaste(), p.getSubCaste(), p.getGotra(), p.getDistrict(),
                    p.getDistrict().division().name(), p.getVillage(), p.getNativePlace(), p.getMotherTongue(),
                    p.getEducation(), p.getOccupation(), p.getBio(), p.getCompleteness());
        }
    }
}
