package in.pahadijodi.horoscope.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.horoscope.api.HoroscopeDtos.*;
import static in.pahadijodi.horoscope.api.HoroscopeDtos.DISCLAIMER;
import in.pahadijodi.horoscope.domain.Manglik;
import in.pahadijodi.horoscope.domain.Manglik.Level;
import in.pahadijodi.horoscope.domain.ManglikEvaluator;
import in.pahadijodi.horoscope.domain.MoonProfile;
import in.pahadijodi.horoscope.domain.Zodiac;
import in.pahadijodi.horoscope.guna.GunaMilanCalculator;
import in.pahadijodi.horoscope.guna.GunaMilanReport;
import in.pahadijodi.horoscope.guna.RuleSet;
import in.pahadijodi.horoscope.persistence.*;
import in.pahadijodi.horoscope.persistence.BirthProfile.Visibility;
import in.pahadijodi.horoscope.port.GeocoderPort;
import in.pahadijodi.horoscope.port.GeocoderPort.Place;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoroscopeService {
    private static final Duration FUTURE_SLACK = Duration.ofDays(1);

    private final BirthProfileRepository births;
    private final UserKundliRepository userKundlis;
    private final KundliRepository kundlis;
    private final KundliService kundliService;
    private final GeocoderPort geocoder;
    private final FieldCipher cipher;
    private final OutboxService outbox;
    private final ObjectMapper json;
    private final RuleSet rules = RuleSet.load("rules/guna-v1.json");
    private final GunaMilanCalculator calculator = new GunaMilanCalculator(rules);
    private final ManglikEvaluator manglikEvaluator;
    /** 7-day style memo of pair reports; invalidated whenever either user's birth data changes. */
    private final Cache<String, GunaMilanReport> matchCache = Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(Duration.ofDays(7)).build();

    public HoroscopeService(BirthProfileRepository births, UserKundliRepository userKundlis, KundliRepository kundlis,
                            KundliService kundliService, GeocoderPort geocoder, FieldCipher cipher, OutboxService outbox,
                            ObjectMapper json, @Value("${pj.horoscope.manglik-include-moon:true}") boolean includeMoon) {
        this.births = births; this.userKundlis = userKundlis; this.kundlis = kundlis;
        this.kundliService = kundliService; this.geocoder = geocoder; this.cipher = cipher;
        this.outbox = outbox; this.json = json;
        this.manglikEvaluator = new ManglikEvaluator(includeMoon);
    }

    // ------------------------------------------------------------------ write

    @Transactional
    public KundliSummary upsert(UUID userId, BirthDetailsRequest r) {
        if (r.birthDate().isAfter(LocalDate.now().plusDays(FUTURE_SLACK.toDays()))) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BIRTH_DATE", "Birth date cannot be in the future");
        }
        if (r.birthDate().isBefore(LocalDate.of(1900, 1, 1))) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BIRTH_DATE", "Birth date is before 1900");
        }
        Place place = geocoder.find(r.placeId())
                .orElseThrow(() -> new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PLACE", "Unknown place: " + r.placeId()));
        String accuracy = r.timeAccuracy() == null ? (r.birthTime() == null ? "UNKNOWN" : "EXACT") : r.timeAccuracy().toUpperCase();
        if (!List.of("EXACT", "APPROX_15M", "APPROX_1H", "UNKNOWN").contains(accuracy)) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIME_ACCURACY", "timeAccuracy must be EXACT, APPROX_15M, APPROX_1H or UNKNOWN");
        }
        LocalTime time = (accuracy.equals("UNKNOWN") || r.birthTime() == null) ? null : LocalTime.parse(r.birthTime());

        KundliEntity chart = kundliService.chartFor(r.birthDate(), time, place);
        Manglik.Assessment mg = manglikEvaluator.assess(Zodiac.rashi(chart.getMarsLongitude()), chart.getLagnaRashi(), chart.getMoonRashi());
        Visibility vis = r.visibility() == null ? Visibility.MATCHES_ONLY : r.visibility();

        BirthProfile bp = births.findById(userId).orElseGet(() -> new BirthProfile(userId));
        bp.update(cipher.encrypt(writeJson(Map.of("birthDate", r.birthDate().toString(), "birthTime", time == null ? "" : time.toString(),
                "timeAccuracy", accuracy, "placeId", place.id()))), chart.getBirthHash(), vis);
        births.save(bp);

        UserKundli uk = userKundlis.findById(userId).orElseGet(() -> new UserKundli(userId));
        uk.update(chart.getId(), mg.level().name(), mg.marsHouseFromLagna(), mg.marsHouseFromMoon(), mg.cancellations());
        userKundlis.save(uk);
        invalidateMatches(userId);

        // no birth date/time/place in events (docs/04: "no birth time in events")
        outbox.publish("pj.horoscope.updated.v1", "HoroscopeUpdated", userId.toString(),
                Map.of("userId", userId.toString(), "moonRashi", chart.getMoonRashi(), "moonNakshatra", chart.getMoonNakshatra(),
                        "manglik", mg.level().name(), "lowConfidence", chart.isLowConfidence(), "shared", bp.isShared()));
        return summary(chart, mg);
    }

    @Transactional
    public void erase(UUID userId) {
        userKundlis.deleteById(userId);
        births.deleteById(userId);
        invalidateMatches(userId);
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public MyHoroscope mine(UUID userId) {
        BirthProfile bp = births.findById(userId).orElseThrow(() ->
                new PjException(HttpStatus.NOT_FOUND, "HOROSCOPE_NOT_FOUND", "Add your birth details first"));
        UserKundli uk = userKundlis.findById(userId).orElseThrow();
        KundliEntity chart = kundlis.findById(uk.getKundliId()).orElseThrow();
        JsonNode p = readJson(cipher.decrypt(bp.getEncPayload()));
        Place place = geocoder.find(p.get("placeId").asText()).orElse(null);
        BirthView birth = new BirthView(LocalDate.parse(p.get("birthDate").asText()),
                p.get("birthTime").asText().isEmpty() ? null : p.get("birthTime").asText(), p.get("timeAccuracy").asText(),
                place == null ? null : new PlaceView(place.id(), place.name(), place.region()), bp.getVisibility());
        return new MyHoroscope(birth, summary(chart, assessmentOf(uk)));
    }

    @Transactional(readOnly = true)
    public MatchResponse match(UUID me, UUID other, String myRole) {
        if (me.equals(other)) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "SELF_MATCH", "Cannot match with yourself");
        boolean iAmBoy = parseRole(myRole);
        BirthProfile mine = births.findById(me).orElseThrow(() ->
                new PjException(HttpStatus.NOT_FOUND, "HOROSCOPE_NOT_FOUND", "Add your birth details first"));
        BirthProfile theirs = births.findById(other).orElseThrow(() ->
                new PjException(HttpStatus.CONFLICT, "BIRTH_DATA_MISSING", "The other member has not added birth details"));
        // privacy: both sides must have opted in (ON_INTEREST is treated as shared in this MVP)
        if (!mine.isShared() || !theirs.isShared()) {
            throw new PjException(HttpStatus.FORBIDDEN, "HOROSCOPE_NOT_SHARED", "Both members must share their horoscope for matching");
        }
        UserKundli ukMe = userKundlis.findById(me).orElseThrow(), ukOther = userKundlis.findById(other).orElseThrow();
        KundliEntity cMe = kundlis.findById(ukMe.getKundliId()).orElseThrow(), cOther = kundlis.findById(ukOther.getKundliId()).orElseThrow();
        MoonProfile pMe = profileOf(cMe), pOther = profileOf(cOther);

        String key = (iAmBoy ? me + ":" + other : other + ":" + me) + ":" + rules.version();
        GunaMilanReport report = matchCache.get(key, k -> iAmBoy ? calculator.compute(pMe, pOther) : calculator.compute(pOther, pMe));
        return response(report, assessmentOf(ukMe), assessmentOf(ukOther), iAmBoy ? "BOY" : "GIRL");
    }

    /** Stateless: nothing is stored, no identities involved. */
    public MatchResponse matchStateless(StatelessMatchRequest r) {
        MoonProfile boy = moonOf(r.boy()), girl = moonOf(r.girl());
        GunaMilanReport report = calculator.compute(boy, girl);
        Manglik.Assessment a = assessmentOf(r.boy()), b = assessmentOf(r.girl());
        return response(report, a, b, "BOY");
    }

    // ------------------------------------------------------------------ helpers

    private MatchResponse response(GunaMilanReport rep, Manglik.Assessment mineOrBoy, Manglik.Assessment otherOrGirl, String role) {
        String status = ManglikEvaluator.pairStatus(mineOrBoy, otherOrGirl);
        String warning = switch (status) {
            case "MISMATCH" -> "One partner is Manglik and the other is not, with no traditional cancellation found.";
            case "MISMATCH_CANCELLED" -> "One partner is Manglik; a traditional cancellation applies.";
            default -> null;
        };
        List<String> flags = new java.util.ArrayList<>(rep.doshaFlags());
        if (status.equals("MISMATCH")) flags.add("MANGLIK_MISMATCH");
        String verdict = switch (rep.band()) {
            case "EXCELLENT" -> "Excellent match";
            case "VERY_GOOD" -> "Very good match";
            case "AVERAGE" -> "Average, acceptable";
            default -> "Not recommended on Guna count alone";
        } + (flags.isEmpty() ? "" : ". Check: " + String.join(", ", flags));
        return new MatchResponse(rep.ruleSetVersion(), role, rep.scores(), rep.total(), rep.max(), rep.band(), List.copyOf(flags),
                new ManglikPair(mineOrBoy.level(), otherOrGirl.level(), status, warning), verdict, DISCLAIMER);
    }

    private static boolean parseRole(String role) {
        if (role == null || role.equalsIgnoreCase("BOY")) return true;
        if (role.equalsIgnoreCase("GIRL")) return false;
        throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ROLE", "role must be BOY or GIRL");
    }

    private MoonProfile moonOf(MoonInput in) {
        try {
            MoonProfile m = MoonProfile.fromNakshatraPada(in.nakshatra(), in.pada());
            if (in.rashi() != null && in.rashi() != m.rashi()) {
                throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INCONSISTENT_MOON",
                        "Nakshatra " + in.nakshatra() + " pada " + in.pada() + " lies in rashi " + m.rashi() + ", not " + in.rashi());
            }
            return in.degreeInRashi() == null ? m : new MoonProfile(m.rashi(), m.nakshatra(), m.pada(), in.degreeInRashi());
        } catch (IllegalArgumentException e) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_MOON", e.getMessage());
        }
    }

    private static Manglik.Assessment assessmentOf(MoonInput in) {
        Level l = in.manglik() == null ? Level.NONE : in.manglik();
        List<String> c = Boolean.TRUE.equals(in.manglikCancelled()) ? List.of("CALLER_ASSERTED") : List.of();
        return new Manglik.Assessment(l, null, 0, c);
    }

    private static Manglik.Assessment assessmentOf(UserKundli uk) {
        return new Manglik.Assessment(Level.valueOf(uk.getManglik()), uk.getMarsHouseLagna(), uk.getMarsHouseMoon(), uk.cancellations());
    }

    private static MoonProfile profileOf(KundliEntity c) {
        return new MoonProfile(c.getMoonRashi(), c.getMoonNakshatra(), c.getMoonPada(), Math.min(c.getMoonDegree(), 29.9999));
    }

    private KundliSummary summary(KundliEntity c, Manglik.Assessment mg) {
        int n = c.getMoonNakshatra();
        return new KundliSummary(new Named(c.getMoonRashi(), rules.rashiNames().get(c.getMoonRashi() - 1)),
                new Named(n, rules.nakshatraNames().get(n - 1)), c.getMoonPada(),
                rules.ganaByNakshatra().get(n - 1), rules.nadiByNakshatra().get(n - 1), rules.yoniAnimalByNakshatra().get(n - 1),
                c.getLagnaRashi() == null ? null : new Named(c.getLagnaRashi(), rules.rashiNames().get(c.getLagnaRashi() - 1)),
                new ManglikView(mg.level(), mg.marsHouseFromLagna(), mg.marsHouseFromMoon(), mg.cancellations()),
                c.isLowConfidence(), c.getAyanamsa(), c.getEngineVersion(), DISCLAIMER);
    }

    private void invalidateMatches(UUID userId) {
        String id = userId.toString();
        matchCache.asMap().keySet().removeIf(k -> k.contains(id));
    }

    private String writeJson(Object o) {
        try { return json.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private JsonNode readJson(String s) {
        try { return json.readTree(s); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
