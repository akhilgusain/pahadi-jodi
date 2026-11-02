package in.pahadijodi.chat.port;

import java.util.UUID;

/** Decides whether {@code sender} may exchange messages / calls with {@code recipient}. */
public interface ChatPolicyPort {
    Decision check(UUID sender, UUID recipient);

    record Decision(boolean allowed, String code, String message) {
        public static Decision allow() { return new Decision(true, null, null); }
        public static Decision deny(String code, String message) { return new Decision(false, code, message); }
    }
}
