package in.pahadijodi.chat.adapter;

import in.pahadijodi.chat.port.EntitlementPort;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Allows everything. Replace with a subscription-service client (entitlement cache) when available. */
@Component
@ConditionalOnProperty(name = "pj.entitlement.provider", havingValue = "fake", matchIfMissing = true)
public class FakeEntitlementAdapter implements EntitlementPort {
    @Override public boolean canChat(UUID userId) { return true; }
    @Override public boolean canVideoCall(UUID userId) { return true; }
}
