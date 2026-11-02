package in.pahadijodi.chat.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory presence (user -> live STOMP sessions). Single-instance only: for multiple chat pods back this
 * with Redis (SET per user with TTL refreshed by heartbeats) behind the same API.
 */
@Service
public class PresenceService {
    private final Map<UUID, Set<String>> sessions = new ConcurrentHashMap<>();

    /** @return true when this is the user's first live session (offline -> online). */
    public boolean connected(UUID user, String sessionId) {
        boolean[] first = {false};
        sessions.compute(user, (k, set) -> {
            if (set == null) { set = ConcurrentHashMap.newKeySet(); }
            if (set.isEmpty()) first[0] = true;
            set.add(sessionId);
            return set;
        });
        return first[0];
    }

    /** @return true when the user's last session closed (online -> offline). */
    public boolean disconnected(UUID user, String sessionId) {
        boolean[] last = {false};
        sessions.computeIfPresent(user, (k, set) -> {
            if (set.remove(sessionId) && set.isEmpty()) { last[0] = true; return null; }
            return set.isEmpty() ? null : set;
        });
        return last[0];
    }

    public boolean isOnline(UUID user) {
        var set = sessions.get(user);
        return set != null && !set.isEmpty();
    }

    public Map<UUID, Boolean> onlineAmong(Collection<UUID> users) {
        Map<UUID, Boolean> out = new HashMap<>();
        for (UUID u : users) out.put(u, isOnline(u));
        return out;
    }
}
