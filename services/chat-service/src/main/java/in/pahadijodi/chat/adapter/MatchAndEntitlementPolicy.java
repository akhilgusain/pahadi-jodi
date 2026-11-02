package in.pahadijodi.chat.adapter;

import in.pahadijodi.chat.config.ChatProperties;
import in.pahadijodi.chat.domain.ChatMatchRepository;
import in.pahadijodi.chat.domain.Conversation;
import in.pahadijodi.chat.port.ChatPolicyPort;
import in.pahadijodi.chat.port.EntitlementPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Chat unlocks only for mutually matched users whose plan allows chat. */
@Component
public class MatchAndEntitlementPolicy implements ChatPolicyPort {
    private final ChatMatchRepository matches;
    private final EntitlementPort entitlements;
    private final boolean requireMatch;

    public MatchAndEntitlementPolicy(ChatMatchRepository matches, EntitlementPort entitlements, ChatProperties props) {
        this.matches = matches;
        this.entitlements = entitlements;
        this.requireMatch = props.policy().requireMatch();
    }

    @Override
    public Decision check(UUID sender, UUID recipient) {
        if (requireMatch) {
            boolean first = Conversation.canonicalFirst(sender, recipient);
            boolean matched = first ? matches.existsByUserAAndUserB(sender, recipient)
                                    : matches.existsByUserAAndUserB(recipient, sender);
            if (!matched) return Decision.deny("NOT_MATCHED", "Chat unlocks after a mutual match");
        }
        if (!entitlements.canChat(sender)) return Decision.deny("PLAN_REQUIRED", "Upgrade your plan to chat");
        return Decision.allow();
    }
}
