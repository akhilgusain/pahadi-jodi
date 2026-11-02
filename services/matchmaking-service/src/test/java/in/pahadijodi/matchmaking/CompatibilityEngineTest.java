package in.pahadijodi.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;

import in.pahadijodi.matchmaking.domain.PartnerPreference;
import in.pahadijodi.matchmaking.domain.PartnerPreference.Dealbreaker;
import in.pahadijodi.matchmaking.domain.ProfileFacet;
import in.pahadijodi.matchmaking.scoring.CompatibilityEngine;
import in.pahadijodi.matchmaking.scoring.Scoring.ScoredMatch;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class CompatibilityEngineTest {
    final CompatibilityEngine engine = new CompatibilityEngine();

    static ProfileFacet f(String gender, int age, String community, String district, String division, String village,
                          String gotra, String edu, String tongue) {
        return new ProfileFacet(UUID.randomUUID(), UUID.randomUUID(), "n", gender, age, community, "Rajput", null, gotra,
                district, division, village, edu, "Engineer", tongue, 80);
    }

    final ProfileFacet her = f("FEMALE", 28, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Kandakhal", "Kashyap", "B.Tech", "Garhwali");

    @Test
    void hardFilters() {
        assertThat(engine.reject(her, null, f("FEMALE", 29, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "x", "Bharadwaj", "B.Tech", "Garhwali"), null))
                .hasValueSatisfying(r -> assertThat(r).contains("Same gender"));
        assertThat(engine.reject(her, null, f("MALE", 30, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "x", "KASHYAP ", "B.Tech", "Garhwali"), null))
                .hasValueSatisfying(r -> assertThat(r).contains("gotra"));
        assertThat(engine.reject(her, null, f("MALE", 30, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "x", "Bharadwaj", "B.Tech", "Garhwali"), null)).isEmpty();
        assertThat(engine.reject(her, null, her, null)).isPresent();   // self
    }

    @Test
    void dealbreakersApplyInBothDirections() {
        var him = f("MALE", 30, "KUMAONI", "ALMORA", "KUMAON", "Jageshwar", "Bharadwaj", "MBA", "Kumaoni");
        var herPref = new PartnerPreference(her.getUserId());
        herPref.apply(null, null, null, null, "GARHWAL", null, null, null, Set.of(Dealbreaker.DIVISION));
        assertThat(engine.reject(her, herPref, him, null)).hasValueSatisfying(r -> assertThat(r).contains("your dealbreaker"));

        var himPref = new PartnerPreference(him.getUserId());
        himPref.apply(25, 27, null, null, null, null, null, null, Set.of(Dealbreaker.AGE));   // she is 28
        assertThat(engine.reject(her, null, him, himPref)).hasValueSatisfying(r -> assertThat(r).contains("their dealbreaker"));

        // the same preference WITHOUT the dealbreaker is only a soft score
        himPref.apply(25, 27, null, null, null, null, null, null, Set.of());
        assertThat(engine.reject(her, null, him, himPref)).isEmpty();
    }

    @Test
    void nativeRegionOrdersSameVillageDistrictDivisionOther() {
        double village = score(f("MALE", 30, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Kandakhal", "A", "B.Tech", "Garhwali"));
        double district = score(f("MALE", 30, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Khirsu", "A", "B.Tech", "Garhwali"));
        double division = score(f("MALE", 30, "GARHWALI", "DEHRADUN", "GARHWAL", "Raipur", "A", "B.Tech", "Garhwali"));
        double other = score(f("MALE", 30, "GARHWALI", "ALMORA", "KUMAON", "Jageshwar", "A", "B.Tech", "Garhwali"));
        assertThat(village).isGreaterThan(district);
        assertThat(district).isGreaterThan(division);
        assertThat(division).isGreaterThan(other);
        assertThat(other).isBetween(0.0, 100.0);
    }

    private double score(ProfileFacet him) {
        return engine.score(her, null, him, null, OptionalInt.empty()).score();
    }

    @Test
    void mutualFitBeatsOneSidedFit() {
        var him = f("MALE", 30, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Kandakhal", "A", "B.Tech", "Garhwali");
        var himPref = new PartnerPreference(him.getUserId());
        double mutual = engine.score(her, null, him, himPref, OptionalInt.empty()).score();

        // he only wants Kumaoni women aged 22-24 in Almora: she scores poorly in HIS direction
        himPref.apply(22, 24, List.of("KUMAONI"), List.of("ALMORA"), "KUMAON", null, null, List.of("Kumaoni"), Set.of());
        double oneSided = engine.score(her, null, him, himPref, OptionalInt.empty()).score();
        assertThat(oneSided).isLessThan(mutual);
    }

    @Test
    void horoscopeFactorOnlyWhenGunaKnown() {
        var him = f("MALE", 30, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Kandakhal", "A", "B.Tech", "Garhwali");
        ScoredMatch without = engine.score(her, null, him, null, OptionalInt.empty());
        ScoredMatch good = engine.score(her, null, him, null, OptionalInt.of(30));
        ScoredMatch poor = engine.score(her, null, him, null, OptionalInt.of(10));
        assertThat(without.breakdown()).noneMatch(b -> b.factor().equals("Horoscope"));
        assertThat(good.breakdown()).anyMatch(b -> b.factor().equals("Horoscope"));
        assertThat(good.score()).isGreaterThan(poor.score());
        assertThat(good.reasons()).anyMatch(r -> r.contains("30/36"));
        assertThat(without.breakdown().stream().mapToDouble(b -> b.weight()).sum()).isBetween(0.99, 1.01);   // renormalised
    }

    @Test
    void explanationNamesSharedRoots() {
        var him = f("MALE", 30, "GARHWALI", "PAURI_GARHWAL", "GARHWAL", "Kandakhal", "A", "B.Tech", "Garhwali");
        var r = engine.score(her, null, him, null, OptionalInt.empty());
        assertThat(r.reasons()).contains("Both from Kandakhal", "Both Garhwali", "Both speak Garhwali");
    }

    @Test
    void educationLevels() {
        assertThat(java.util.stream.Stream.of("B.Tech", "M.Tech", "MBA", "PhD", "Diploma in Civil", "12th pass", "", "Postgraduate", "BSc", "B.A.")
                .map(e -> CompatibilityEngineAccess.level(e)).toList()).containsExactly(3, 4, 4, 5, 2, 1, 0, 4, 3, 3);
    }
}
