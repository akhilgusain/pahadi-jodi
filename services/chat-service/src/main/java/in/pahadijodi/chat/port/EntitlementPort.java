package in.pahadijodi.chat.port;

import java.util.UUID;

/** Plan entitlements (subscription-service). Only a fake adapter exists so far. */
public interface EntitlementPort {
    boolean canChat(UUID userId);
    boolean canVideoCall(UUID userId);
}
