package in.pahadijodi.matchmaking.service;

import in.pahadijodi.matchmaking.domain.ProfileFacet;
import java.util.UUID;

/** Public card for another member. Never includes userId or contact data. */
public final class Views {
    private Views() {}

    public record ProfileCard(UUID profileId, String displayName, Integer age, String gender, String community,
                              String district, String division, String village, String education, String occupation,
                              int completeness) {
        public static ProfileCard of(ProfileFacet f) {
            return new ProfileCard(f.getProfileId(), f.getDisplayName(), f.getAge(), f.getGender(), f.getCommunity(),
                    f.getDistrict(), f.getDivision(), f.getVillage(), f.getEducation(), f.getOccupation(), f.getCompleteness());
        }
    }
}
