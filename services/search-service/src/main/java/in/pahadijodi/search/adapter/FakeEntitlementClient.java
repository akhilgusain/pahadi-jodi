package in.pahadijodi.search.adapter;

import in.pahadijodi.search.port.EntitlementClient;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Allow-all stand-in until subscription-service is integrated. */
@Component
@ConditionalOnMissingBean(value = EntitlementClient.class, ignored = FakeEntitlementClient.class)
public class FakeEntitlementClient implements EntitlementClient {
    @Override
    public Entitlements forUser(UUID userId) {
        return new Entitlements(50, true, 20);
    }
}
