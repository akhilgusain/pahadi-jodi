package in.pahadijodi.horoscope.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_kundli")
public class UserKundli {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "kundli_id", nullable = false) private UUID kundliId;
    @Column(nullable = false) private String manglik;
    @Column(name = "mars_house_lagna") private Integer marsHouseLagna;
    @Column(name = "mars_house_moon", nullable = false) private int marsHouseMoon;
    @Column(name = "manglik_cancellations", nullable = false) private String manglikCancellations;
    @Column(name = "computed_at", nullable = false) private Instant computedAt;

    protected UserKundli() {}
    public UserKundli(UUID userId) { this.userId = userId; }

    public void update(UUID kundliId, String manglik, Integer marsHouseLagna, int marsHouseMoon, List<String> cancellations) {
        this.kundliId = kundliId;
        this.manglik = manglik;
        this.marsHouseLagna = marsHouseLagna;
        this.marsHouseMoon = marsHouseMoon;
        this.manglikCancellations = String.join(",", cancellations);
        this.computedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public UUID getKundliId() { return kundliId; }
    public String getManglik() { return manglik; }
    public Integer getMarsHouseLagna() { return marsHouseLagna; }
    public int getMarsHouseMoon() { return marsHouseMoon; }
    public List<String> cancellations() {
        return manglikCancellations.isEmpty() ? List.of() : Arrays.asList(manglikCancellations.split(","));
    }
}
