package in.pahadijodi.horoscope.adapter;

import in.pahadijodi.horoscope.port.GeocoderPort;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Small built-in gazetteer (Uttarakhand towns + major Indian and diaspora cities): classpath rules/places.csv. */
@Component
public class BuiltInGeocoder implements GeocoderPort {
    private final Map<String, Place> byId = new LinkedHashMap<>();

    public BuiltInGeocoder() {
        try (var r = new BufferedReader(new InputStreamReader(
                new ClassPathResource("rules/places.csv").getInputStream(), StandardCharsets.UTF_8))) {
            r.readLine(); // header
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] c = line.split(",", -1);
                byId.put(c[0], new Place(c[0], c[1], c[2], Double.parseDouble(c[3]), Double.parseDouble(c[4]), c[5]));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load places.csv", e);
        }
    }

    @Override
    public List<Place> search(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Place> out = new ArrayList<>();
        for (Place p : byId.values()) {
            if (q.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(q) || p.id().contains(q)) out.add(p);
            if (out.size() >= limit) break;
        }
        return out;
    }

    @Override
    public Optional<Place> find(String placeId) { return Optional.ofNullable(byId.get(placeId)); }
}
