package in.pahadijodi.horoscope.api;

import static in.pahadijodi.common.security.ResourceServerAutoConfiguration.userId;

import in.pahadijodi.horoscope.api.HoroscopeDtos.*;
import in.pahadijodi.horoscope.port.GeocoderPort;
import in.pahadijodi.horoscope.service.HoroscopeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/horoscope")
public class HoroscopeController {
    private final HoroscopeService service;
    private final GeocoderPort geocoder;

    public HoroscopeController(HoroscopeService service, GeocoderPort geocoder) {
        this.service = service;
        this.geocoder = geocoder;
    }

    /** Save birth details; returns the computed kundli summary. */
    @PutMapping("/me")
    KundliSummary save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BirthDetailsRequest r) {
        return service.upsert(userId(jwt), r);
    }

    @GetMapping("/me")
    MyHoroscope mine(@AuthenticationPrincipal Jwt jwt) { return service.mine(userId(jwt)); }

    @DeleteMapping("/me")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void erase(@AuthenticationPrincipal Jwt jwt) { service.erase(userId(jwt)); }

    /** Guna Milan between me and another member. {@code role} is MY role in the pair: BOY (default) or GIRL. */
    @GetMapping("/match/{otherUserId}")
    MatchResponse match(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID otherUserId,
                        @RequestParam(required = false) String role) {
        return service.match(userId(jwt), otherUserId, role);
    }

    /** Stateless matching of two explicit Moon placements; nothing is stored. */
    @PostMapping("/match")
    MatchResponse matchStateless(@Valid @RequestBody StatelessMatchRequest r) { return service.matchStateless(r); }

    @GetMapping("/places")
    List<PlaceView> places(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "10") int limit) {
        return geocoder.search(q, Math.min(Math.max(limit, 1), 25)).stream()
                .map(p -> new PlaceView(p.id(), p.name(), p.region())).toList();
    }
}
