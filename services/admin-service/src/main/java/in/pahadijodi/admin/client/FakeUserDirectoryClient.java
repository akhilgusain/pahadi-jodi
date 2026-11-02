package in.pahadijodi.admin.client;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** pj.admin.provider=fake (default): 25 deterministic sample Pahadi users held in memory. */
@Component
@ConditionalOnProperty(name = "pj.admin.provider", havingValue = "fake", matchIfMissing = true)
public class FakeUserDirectoryClient implements UserDirectoryClient {
    private static final String[] NAMES = {"Anjali Rawat", "Rohit Bisht", "Pooja Negi", "Deepak Joshi", "Sneha Bhandari", "Vikas Rana",
            "Kavita Pant", "Mohit Chauhan", "Neha Kandari", "Amit Bora", "Ritu Bisht", "Sandeep Panwar", "Priyanka Dhami",
            "Gaurav Mehra", "Shweta Semwal", "Ankit Kothiyal", "Divya Uniyal", "Nitin Rawat", "Meena Karki", "Harish Adhikari",
            "Swati Nautiyal", "Kunal Bhatt", "Isha Rautela", "Manish Pandey", "Tanuja Fartyal"};
    private static final String[] DISTRICTS = {"DEHRADUN", "PAURI_GARHWAL", "ALMORA", "NAINITAL", "TEHRI_GARHWAL", "CHAMOLI",
            "PITHORAGARH", "HARIDWAR", "UDHAM_SINGH_NAGAR", "BAGESHWAR"};
    private static final String[] PLANS = {"FREE", "FREE", "PREMIUM", "FREE", "PREMIUM_PLUS", "VIP"};

    private final Map<UUID, UserSummary> users = new ConcurrentHashMap<>();
    /** Side effects recorded for inspection in tests/dev: "FORCE_LOGOUT:<id>" etc. */
    public final List<String> actions = new CopyOnWriteArrayList<>();

    public FakeUserDirectoryClient() {
        Instant base = Instant.parse("2026-01-10T08:00:00Z");
        for (int i = 0; i < NAMES.length; i++) {
            UUID id = idFor(i);
            String email = NAMES[i].toLowerCase().replace(' ', '.') + "@example.in";
            users.put(id, new UserSummary(id, UserDirectoryClient.maskEmail(email), NAMES[i], DISTRICTS[i % DISTRICTS.length],
                    "ACTIVE", PLANS[i % PLANS.length], base.plus(i * 9L, ChronoUnit.DAYS)));
        }
    }

    public static UUID idFor(int index) {
        return UUID.nameUUIDFromBytes(("pj-sample-user-" + index).getBytes());
    }

    @Override
    public PageResult<UserSummary> search(String q, int page, int size) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<UserSummary> all = users.values().stream()
                .filter(u -> needle.isEmpty() || u.displayName().toLowerCase().contains(needle)
                        || u.maskedEmail().toLowerCase().contains(needle) || u.district().toLowerCase().contains(needle)
                        || u.id().toString().startsWith(needle))
                .sorted(Comparator.comparing(UserSummary::createdAt).thenComparing(UserSummary::displayName)).toList();
        int from = Math.min(page * size, all.size());
        return new PageResult<>(all.subList(from, Math.min(from + size, all.size())), all.size(), page, size);
    }

    @Override public Optional<UserSummary> find(UUID id) { return Optional.ofNullable(users.get(id)); }

    @Override public void deactivate(UUID id) { users.computeIfPresent(id, (k, u) -> with(u, "DEACTIVATED")); actions.add("DEACTIVATE:" + id); }

    @Override
    public void setBanned(UUID id, boolean banned, String reason, Instant until) {
        users.computeIfPresent(id, (k, u) -> with(u, banned ? "BANNED" : "ACTIVE"));
        actions.add((banned ? "BAN:" : "UNBAN:") + id);
    }

    @Override public void forceLogout(UUID id) { actions.add("FORCE_LOGOUT:" + id); }

    private static UserSummary with(UserSummary u, String status) {
        return new UserSummary(u.id(), u.maskedEmail(), u.displayName(), u.district(), status, u.plan(), u.createdAt());
    }
}
