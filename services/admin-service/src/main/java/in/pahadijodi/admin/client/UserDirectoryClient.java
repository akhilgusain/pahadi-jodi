package in.pahadijodi.admin.client;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port to the user/profile services for back-office operations. */
public interface UserDirectoryClient {
    record UserSummary(UUID id, String maskedEmail, String displayName, String district, String status, String plan, Instant createdAt) {}
    record PageResult<T>(List<T> items, long total, int page, int size) {}

    PageResult<UserSummary> search(String query, int page, int size);

    Optional<UserSummary> find(UUID id);

    void deactivate(UUID id);

    void setBanned(UUID id, boolean banned, String reason, Instant until);

    void forceLogout(UUID id);

    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "";
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }
}
