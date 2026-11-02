package in.pahadijodi.chat.port;

import java.util.UUID;

/** Synchronous pre-send moderation hook (async deep moderation happens in moderation-service). */
public interface ModerationPort {
    Verdict check(UUID sender, String text);

    enum Action { ALLOW, FLAG, BLOCK }

    record Verdict(Action action, String reason) {
        public static Verdict allow() { return new Verdict(Action.ALLOW, null); }
    }
}
