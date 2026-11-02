package in.pahadijodi.notification.adapter;

import in.pahadijodi.notification.port.ContactDirectoryPort;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Deterministic placeholder contacts so every channel has an address locally. Never use outside dev. */
@Component
@ConditionalOnProperty(name = "pj.notification.provider", havingValue = "fake", matchIfMissing = true)
public class FakeContactDirectory implements ContactDirectoryPort {
    @Override
    public Contact lookup(UUID userId) {
        long digits = Math.abs(userId.getLeastSignificantBits() % 1_000_000_000L);
        return new Contact(userId + "@example.invalid", "+919" + String.format("%09d", digits));
    }
}
