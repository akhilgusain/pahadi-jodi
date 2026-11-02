package in.pahadijodi.horoscope.port;

import java.util.List;
import java.util.Optional;

public interface GeocoderPort {
    List<Place> search(String query, int limit);

    Optional<Place> find(String placeId);

    record Place(String id, String name, String region, double latitude, double longitude, String timeZone) {}
}
