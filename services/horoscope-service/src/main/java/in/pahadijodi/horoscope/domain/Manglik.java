package in.pahadijodi.horoscope.domain;

import java.util.List;

public final class Manglik {
    private Manglik() {}

    public enum Level { NONE, PARTIAL, FULL }

    /** @param marsHouseFromLagna null when birth time is unknown (no ascendant) */
    public record Assessment(Level level, Integer marsHouseFromLagna, int marsHouseFromMoon, List<String> cancellations) {}
}
