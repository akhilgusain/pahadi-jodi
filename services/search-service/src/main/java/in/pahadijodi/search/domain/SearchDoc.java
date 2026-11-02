package in.pahadijodi.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Searchable projection of a profile (payload of pj.profile.updated.v1). Facets only: no contact data,
 * no date of birth (age is a derived number), no document or verification details.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchDoc(UUID profileId, UUID userId, String displayName, String gender, Integer age,
                        String community, String caste, String subCaste, String gotra, String district,
                        String division, String village, String education, String occupation,
                        String motherTongue, Integer completeness) {
}
