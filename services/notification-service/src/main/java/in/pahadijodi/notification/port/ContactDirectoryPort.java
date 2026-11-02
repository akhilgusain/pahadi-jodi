package in.pahadijodi.notification.port;

import java.util.UUID;

/** Resolves a user's contact points (events carry ids only, never PII). Real adapter: user-service REST/cache. */
public interface ContactDirectoryPort {
    Contact lookup(UUID userId);

    record Contact(String email, String phone) {}
}
