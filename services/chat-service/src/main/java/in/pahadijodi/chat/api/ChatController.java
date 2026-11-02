package in.pahadijodi.chat.api;

import in.pahadijodi.chat.domain.MessageType;
import in.pahadijodi.chat.service.ChatService;
import in.pahadijodi.chat.service.Dtos.*;
import in.pahadijodi.chat.service.PresenceService;
import in.pahadijodi.chat.ws.ChatDispatcher;
import in.pahadijodi.common.security.ResourceServerAutoConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    public record OpenConversationRequest(@NotNull UUID peerId) {}
    public record SendRequest(MessageType type, @Size(max = 4000) String body, @Size(max = 1000) String mediaUrl,
                              Integer mediaDurationSec, @Size(max = 64) String clientMsgId) {}
    public record ReadRequest(@NotNull Long upToMessageId) {}
    public record ReactionRequest(@NotNull @Size(min = 1, max = 16) String emoji) {}
    public record UnreadCount(long unread) {}

    private final ChatService chat;
    private final PresenceService presence;
    private final ChatDispatcher dispatcher;

    public ChatController(ChatService chat, PresenceService presence, ChatDispatcher dispatcher) {
        this.chat = chat;
        this.presence = presence;
        this.dispatcher = dispatcher;
    }

    @PostMapping("/conversations")
    ConversationDto open(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OpenConversationRequest r) {
        return chat.openConversation(id(jwt), r.peerId());
    }

    @GetMapping("/conversations")
    java.util.List<ConversationDto> list(@AuthenticationPrincipal Jwt jwt) { return chat.conversations(id(jwt)); }

    @GetMapping("/unread-count")
    UnreadCount unread(@AuthenticationPrincipal Jwt jwt) { return new UnreadCount(chat.totalUnread(id(jwt))); }

    @GetMapping("/conversations/{id}/messages")
    HistoryPage history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                        @RequestParam(required = false) Long before, @RequestParam(defaultValue = "30") int limit) {
        return chat.history(id(jwt), id, before, limit);
    }

    /** REST fallback for sending (the primary path is STOMP /app/chat.send). Still pushes to live sessions. */
    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageDto send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody SendRequest r) {
        MessageDto m = chat.send(id(jwt), new SendCommand(id, r.type(), r.body(), r.mediaUrl(), r.mediaDurationSec(), r.clientMsgId()));
        dispatcher.message(m);
        return m;
    }

    @PostMapping("/conversations/{id}/read")
    Map<String, Object> read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ReadRequest r) {
        var receipt = chat.markRead(id(jwt), id, r.upToMessageId());
        receipt.ifPresent(dispatcher::read);
        return Map.of("updated", receipt.map(ReadReceipt::count).orElse(0));
    }

    @PutMapping("/messages/{id}/reaction")
    ReactionUpdate react(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody ReactionRequest r) {
        var u = chat.react(id(jwt), id, r.emoji());
        dispatcher.reaction(u);
        return u;
    }

    @DeleteMapping("/messages/{id}/reaction")
    ReactionUpdate unreact(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        var u = chat.removeReaction(id(jwt), id);
        dispatcher.reaction(u);
        return u;
    }

    @PostMapping("/messages/{id}/translate")
    TranslationDto translate(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestParam String to) {
        return chat.translate(id(jwt), id, to);
    }

    @GetMapping("/presence")
    Map<UUID, Boolean> presence(@RequestParam String userIds) {
        var ids = Arrays.stream(userIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).limit(200)
                .map(UUID::fromString).collect(Collectors.toList());
        return presence.onlineAmong(ids);
    }

    private static UUID id(Jwt jwt) { return ResourceServerAutoConfiguration.userId(jwt); }
}
