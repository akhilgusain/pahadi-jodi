package in.pahadijodi.horoscope.api;

import in.pahadijodi.horoscope.domain.Manglik;
import in.pahadijodi.horoscope.guna.KootaScore;
import in.pahadijodi.horoscope.persistence.BirthProfile.Visibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;

public final class HoroscopeDtos {
    private HoroscopeDtos() {}

    public static final String DISCLAIMER =
            "Traditional indicator, not a verdict. Charts use an approximate ephemeris (see docs); confirm with a pandit.";

    public record BirthDetailsRequest(
            @NotNull LocalDate birthDate,
            @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "HH:mm") String birthTime,
            String timeAccuracy,
            @NotBlank String placeId,
            Visibility visibility) {}

    public record Named(int number, String name) {}

    public record ManglikView(Manglik.Level level, Integer marsHouseFromLagna, int marsHouseFromMoon, List<String> cancellations) {}

    public record KundliSummary(Named moonRashi, Named nakshatra, int pada, String gana, String nadi, String yoniAnimal,
                                Named lagnaRashi, ManglikView manglik, boolean lowConfidence,
                                String ayanamsa, String engineVersion, String disclaimer) {}

    public record PlaceView(String id, String name, String region) {}

    public record BirthView(LocalDate birthDate, String birthTime, String timeAccuracy, PlaceView place, Visibility visibility) {}

    public record MyHoroscope(BirthView birth, KundliSummary kundli) {}

    public record ManglikPair(Manglik.Level a, Manglik.Level b, String status, String warning) {}

    public record MatchResponse(String ruleSetVersion, String myRole, List<KootaScore> scores, double total, double max,
                                String band, List<String> doshaFlags, ManglikPair manglik, String verdict, String disclaimer) {}

    /** One person for the stateless endpoint: nakshatra+pada are enough (rashi is implied; if given it must agree). */
    public record MoonInput(@Min(1) @Max(27) int nakshatra, @Min(1) @Max(4) int pada,
                            @Min(1) @Max(12) Integer rashi, Double degreeInRashi,
                            Manglik.Level manglik, Boolean manglikCancelled) {}

    public record StatelessMatchRequest(@NotNull @Valid MoonInput boy, @NotNull @Valid MoonInput girl) {}
}
