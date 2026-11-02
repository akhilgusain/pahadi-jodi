package in.pahadijodi.airec.service;

import in.pahadijodi.airec.domain.ProfileProjection;
import org.springframework.stereotype.Component;

/**
 * Hook: may {@code viewer} see {@code target}? The skeleton allows everything. Wire this to profile-service privacy settings and
 * the block/ignore lists from matchmaking-service before launch (blocked, hidden, suspended profiles must never be surfaced).
 */
@Component
public class ProfileVisibility {
    public boolean canView(ProfileProjection viewer, ProfileProjection target) { return true; }
}
