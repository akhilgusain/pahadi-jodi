package in.pahadijodi.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Filter set shared by the live search endpoint and saved searches. All fields optional. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchCriteria(Integer ageMin, Integer ageMax, String gender, List<String> community, String caste,
                             List<String> district, String division, String village, String education,
                             String occupation, String motherTongue, String q) {

    /** Filters a FREE plan may not use (see EntitlementClient): everything beyond the basics. */
    public boolean usesAdvancedFilters() {
        return has(caste) || has(village) || has(education) || has(occupation) || has(motherTongue);
    }

    private static boolean has(String s) { return s != null && !s.isBlank(); }
}
