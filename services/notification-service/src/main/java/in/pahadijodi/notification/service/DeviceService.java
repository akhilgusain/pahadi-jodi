package in.pahadijodi.notification.service;

import in.pahadijodi.notification.domain.DeviceToken;
import in.pahadijodi.notification.domain.DeviceTokenRepository;
import in.pahadijodi.notification.domain.Platform;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {
    static final int MAX_DEVICES_PER_USER = 10;
    private final DeviceTokenRepository repo;
    private final Clock clock;

    public DeviceService(DeviceTokenRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** Upsert by token; a token moves to the newest account that registers it. Keeps the 10 most recent per user. */
    @Transactional
    public void register(UUID userId, String token, Platform platform) {
        var now = clock.instant();
        var existing = repo.findByToken(token);
        if (existing.isPresent()) existing.get().claim(userId, platform, now);
        else repo.save(new DeviceToken(userId, token, platform, now));
        repo.flush();
        List<DeviceToken> all = repo.findByUserIdOrderByLastSeenAtDesc(userId);
        if (all.size() > MAX_DEVICES_PER_USER) repo.deleteAll(all.subList(MAX_DEVICES_PER_USER, all.size()));
    }

    @Transactional
    public boolean unregister(UUID userId, String token) { return repo.deleteByUserIdAndToken(userId, token) > 0; }
}
