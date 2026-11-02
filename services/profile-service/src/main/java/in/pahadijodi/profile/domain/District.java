package in.pahadijodi.profile.domain;

/** All 13 districts with their division (Garhwal / Kumaon). Source: docs/02 section 7. */
public enum District {
    DEHRADUN(Division.GARHWAL), HARIDWAR(Division.GARHWAL), PAURI_GARHWAL(Division.GARHWAL),
    TEHRI_GARHWAL(Division.GARHWAL), CHAMOLI(Division.GARHWAL), RUDRAPRAYAG(Division.GARHWAL),
    UTTARKASHI(Division.GARHWAL),
    ALMORA(Division.KUMAON), NAINITAL(Division.KUMAON), BAGESHWAR(Division.KUMAON),
    PITHORAGARH(Division.KUMAON), CHAMPAWAT(Division.KUMAON), UDHAM_SINGH_NAGAR(Division.KUMAON);

    public enum Division { GARHWAL, KUMAON }

    private final Division division;
    District(Division d) { this.division = d; }
    public Division division() { return division; }
}
