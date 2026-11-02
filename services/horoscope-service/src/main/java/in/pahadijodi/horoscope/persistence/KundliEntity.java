package in.pahadijodi.horoscope.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Immutable chart for (birthHash, ayanamsa, engineVersion). Never updated, only inserted. */
@Entity
@Table(name = "kundli")
public class KundliEntity {
    @Id private UUID id;
    @Column(name = "birth_hash", nullable = false) private String birthHash;
    @Column(nullable = false) private String ayanamsa;
    @Column(name = "engine_version", nullable = false) private String engineVersion;
    @Column(name = "lagna_rashi") private Integer lagnaRashi;
    @Column(name = "lagna_longitude") private Double lagnaLongitude;
    @Column(name = "moon_rashi", nullable = false) private int moonRashi;
    @Column(name = "moon_nakshatra", nullable = false) private int moonNakshatra;
    @Column(name = "moon_pada", nullable = false) private int moonPada;
    @Column(name = "moon_degree", nullable = false) private double moonDegree;
    @Column(name = "moon_longitude", nullable = false) private double moonLongitude;
    @Column(name = "mars_longitude", nullable = false) private double marsLongitude;
    @Column(name = "low_confidence", nullable = false) private boolean lowConfidence;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected KundliEntity() {}

    public KundliEntity(String birthHash, String ayanamsa, String engineVersion, Integer lagnaRashi, Double lagnaLongitude,
                        int moonRashi, int moonNakshatra, int moonPada, double moonDegree, double moonLongitude,
                        double marsLongitude, boolean lowConfidence) {
        this.id = UUID.randomUUID();
        this.birthHash = birthHash; this.ayanamsa = ayanamsa; this.engineVersion = engineVersion;
        this.lagnaRashi = lagnaRashi; this.lagnaLongitude = lagnaLongitude;
        this.moonRashi = moonRashi; this.moonNakshatra = moonNakshatra; this.moonPada = moonPada;
        this.moonDegree = moonDegree; this.moonLongitude = moonLongitude; this.marsLongitude = marsLongitude;
        this.lowConfidence = lowConfidence;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getBirthHash() { return birthHash; }
    public String getAyanamsa() { return ayanamsa; }
    public String getEngineVersion() { return engineVersion; }
    public Integer getLagnaRashi() { return lagnaRashi; }
    public int getMoonRashi() { return moonRashi; }
    public int getMoonNakshatra() { return moonNakshatra; }
    public int getMoonPada() { return moonPada; }
    public double getMoonDegree() { return moonDegree; }
    public double getMarsLongitude() { return marsLongitude; }
    public boolean isLowConfidence() { return lowConfidence; }
}
