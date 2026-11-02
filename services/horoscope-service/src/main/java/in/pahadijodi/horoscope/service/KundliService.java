package in.pahadijodi.horoscope.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import in.pahadijodi.horoscope.domain.MoonProfile;
import in.pahadijodi.horoscope.domain.Zodiac;
import in.pahadijodi.horoscope.persistence.KundliEntity;
import in.pahadijodi.horoscope.persistence.KundliRepository;
import in.pahadijodi.horoscope.port.EphemerisPort;
import in.pahadijodi.horoscope.port.GeocoderPort.Place;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Charts are immutable per (birth hash, ayanamsa, engine version): resolved from L1 (Caffeine), then the table, then computed. */
@Service
public class KundliService {
    private final EphemerisPort ephemeris;
    private final KundliRepository repo;
    private final Cache<String, KundliEntity> l1 = Caffeine.newBuilder().maximumSize(50_000).expireAfterAccess(Duration.ofHours(24)).build();

    public KundliService(EphemerisPort ephemeris, KundliRepository repo) {
        this.ephemeris = ephemeris;
        this.repo = repo;
    }

    /** sha256 over the normalised inputs; the only link between a person and a chart row. */
    public static String birthHash(LocalDate date, LocalTime timeOrNull, Place place) {
        String s = String.format(Locale.ROOT, "%s|%s|%.4f|%.4f|%s", date, timeOrNull == null ? "unknown" : timeOrNull.withSecond(0).withNano(0),
                place.latitude(), place.longitude(), place.timeZone());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public KundliEntity chartFor(LocalDate date, LocalTime timeOrNull, Place place) {
        String hash = birthHash(date, timeOrNull, place);
        // the engine version is part of the key so an engine upgrade produces new rows and never mutates old ones
        String ayan = "LAHIRI";
        String engine = engineVersion;
        String key = hash + "|" + ayan + "|" + engine;
        KundliEntity hit = l1.getIfPresent(key);
        if (hit != null) return hit;
        KundliEntity row = repo.findByBirthHashAndAyanamsaAndEngineVersion(hash, ayan, engine).orElseGet(() -> {
            EphemerisPort.Chart c = ephemeris.compute(date, timeOrNull, place.latitude(), place.longitude(), ZoneId.of(place.timeZone()));
            MoonProfile moon = MoonProfile.fromLongitude(c.moonLongitude());
            Integer lagnaRashi = c.ascendantLongitude() == null ? null : Zodiac.rashi(c.ascendantLongitude());
            return repo.save(new KundliEntity(hash, c.ayanamsa(), c.engineVersion(), lagnaRashi, c.ascendantLongitude(),
                    moon.rashi(), moon.nakshatra(), moon.pada(), moon.degreeInRashi(), c.moonLongitude(),
                    c.marsLongitude(), c.lowConfidence()));
        });
        l1.put(key, row);
        return row;
    }

    // Adapters report their version on every chart, so resolve it once with a cheap probe computation.
    // With provider=real and no bundled Swiss Ephemeris this fails fast at startup, by design.
    private String engineVersion;

    @jakarta.annotation.PostConstruct
    void init() {
        engineVersion = ephemeris.compute(LocalDate.of(2000, 1, 1), null, 0, 0, ZoneId.of("UTC")).engineVersion();
    }
}
