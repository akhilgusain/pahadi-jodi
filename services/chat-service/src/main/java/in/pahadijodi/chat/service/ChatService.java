package in.pahadijodi.chat.service;

import in.pahadijodi.chat.domain.*;
import in.pahadijodi.chat.port.ChatPolicyPort;
import in.pahadijodi.chat.port.ModerationPort;
import in.pahadijodi.chat.port.ModerationPort.Action;
import in.pahadijodi.chat.port.MessageStore;
import in.pahadijodi.chat.port.TranslatorPort;
import in.pahadijodi.chat.service.Dtos.*;
import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    public static final String TOPIC_MESSAGE_SENT = "pj.chat.message-sent.v1";
    static final int MAX_TEXT = 2000;
    static final int MAX_VOICE_SECONDS = 300;
    private static final Set<String> TRANSLATION_LANGS = Set.of("en", "hi");

    private final ConversationRepository conversations;
    private final MessageStore store;
    private final MessageReactionRepository reactions;
    private final ChatMatchRepository matches;
    private final ChatPolicyPort policy;
    private final ModerationPort moderation;
    private final TranslatorPort translator;
    private final PresenceService presence;
    private final OutboxService outbox;

    /** Small LRU so repeated "translate" taps do not pay for the LLM twice. */
    private final Map<String, String> translationCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > 500; }
            });

    public ChatService(ConversationRepository conversations, MessageStore store, MessageReactionRepository reactions,
                       ChatMatchRepository matches, ChatPolicyPort policy, ModerationPort moderation,
                       TranslatorPort translator, PresenceService presence, OutboxService outbox) {
        this.conversations = conversations;
        this.store = store;
        this.reactions = reactions;
        this.matches = matches;
        this.policy = policy;
        this.moderation = moderation;
        this.translator = translator;
        this.presence = presence;
        this.outbox = outbox;
    }

    // ---- conversations ---------------------------------------------------------------------------------------

    @Transactional
    public ConversationDto openConversation(UUID me, UUID peer) {
        if (me.equals(peer)) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "CANNOT_CHAT_SELF", "Cannot chat with yourself");
        var decision = policy.check(me, peer);
        if (!decision.allowed()) throw new PjException(HttpStatus.FORBIDDEN, decision.code(), decision.message());
        return view(me, ensureConversation(me, peer), unreadMap(me));
    }

    /** Idempotent; no policy check (used by the match-created consumer and after policy checks). */
    @Transactional
    public Conversation ensureConversation(UUID x, UUID y) {
        boolean xFirst = Conversation.canonicalFirst(x, y);
        UUID a = xFirst ? x : y, b = xFirst ? y : x;
        return conversations.findByUserAAndUserB(a, b).orElseGet(() -> conversations.save(new Conversation(a, b)));
    }

    @Transactional
    public void recordMatch(UUID x, UUID y, String matchRef) {
        boolean xFirst = Conversation.canonicalFirst(x, y);
        UUID a = xFirst ? x : y, b = xFirst ? y : x;
        if (!matches.existsByUserAAndUserB(a, b)) matches.save(new ChatMatch(a, b, matchRef));
        ensureConversation(a, b);
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> conversations(UUID me) {
        var unread = unreadMap(me);
        return conversations.findForUser(me).stream().map(c -> view(me, c, unread)).toList();
    }

    @Transactional(readOnly = true)
    public long totalUnread(UUID me) {
        return unreadMap(me).values().stream().mapToLong(Long::longValue).sum();
    }

    @Transactional(readOnly = true)
    public List<UUID> peersOf(UUID user) {
        return conversations.findForUser(user).stream().map(c -> c.peerOf(user)).toList();
    }

    @Transactional(readOnly = true)
    public UUID peerInConversation(UUID me, UUID conversationId) {
        return requireMember(conversationId, me).peerOf(me);
    }

    // ---- messages --------------------------------------------------------------------------------------------

    @Transactional
    public MessageDto send(UUID me, SendCommand c) {
        if (c.conversationId() == null) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "conversationId is required");
        Conversation conv = requireMember(c.conversationId(), me);
        UUID peer = conv.peerOf(me);
        var decision = policy.check(me, peer);
        if (!decision.allowed()) throw new PjException(HttpStatus.FORBIDDEN, decision.code(), decision.message());

        MessageType type = c.type() == null ? MessageType.TEXT : c.type();
        validateContent(type, c);
        String clientMsgId = (c.clientMsgId() == null || c.clientMsgId().isBlank()) ? UUID.randomUUID().toString() : c.clientMsgId();
        if (clientMsgId.length() > 64) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "clientMsgId too long");

        // Idempotent retry from a flaky mobile connection: return the original message.
        var existing = store.findByClientMsgId(conv.getId(), me, clientMsgId);
        if (existing.isPresent()) return toDto(existing.get(), List.of());

        boolean flagged = false;
        var verdict = moderation.check(me, c.body());
        if (verdict.action() == Action.BLOCK) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "MESSAGE_BLOCKED", "This message violates our community guidelines");
        }
        if (verdict.action() == Action.FLAG) flagged = true;

        ChatMessage m = new ChatMessage(conv.getId(), me, peer, type, trimToNull(c.body()), trimToNull(c.mediaUrl()),
                c.mediaDurationSec(), clientMsgId, flagged);
        Instant now = Instant.now();
        if (presence.isOnline(peer)) m.markDelivered(now);
        m = store.save(m);
        conv.touch(m.getId(), now);

        // Ids only: no message body ever leaves this service in an event.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", String.valueOf(m.getId()));
        payload.put("conversationId", conv.getId().toString());
        payload.put("senderUserId", me.toString());
        payload.put("recipientUserId", peer.toString());
        payload.put("type", type.name());
        payload.put("flagged", flagged);
        payload.put("sentAt", m.getCreatedAt().toString());
        outbox.publish(TOPIC_MESSAGE_SENT, "MessageSent", conv.getId().toString(), payload);
        return toDto(m, List.of());
    }

    @Transactional(readOnly = true)
    public HistoryPage history(UUID me, UUID conversationId, Long before, int limit) {
        requireMember(conversationId, me);
        int lim = Math.max(1, Math.min(limit, 100));
        List<ChatMessage> rows = store.history(conversationId, before, lim + 1);
        boolean more = rows.size() > lim;
        List<ChatMessage> page = more ? rows.subList(0, lim) : rows;
        Map<Long, List<MessageReaction>> rx = reactionsFor(page);
        List<MessageDto> items = page.stream().map(m -> toDto(m, rx.getOrDefault(m.getId(), List.of()))).toList();
        return new HistoryPage(items, more ? page.get(page.size() - 1).getId() : null);
    }

    @Transactional
    public Optional<ReadReceipt> markRead(UUID me, UUID conversationId, long upToMessageId) {
        Conversation conv = requireMember(conversationId, me);
        int n = store.markRead(conversationId, me, upToMessageId, Instant.now());
        return n == 0 ? Optional.empty()
                : Optional.of(new ReadReceipt(conversationId, me, conv.peerOf(me), upToMessageId, n));
    }

    /** Called when a user comes online: everything addressed to them becomes DELIVERED. */
    @Transactional
    public List<DeliveryReceipt> deliverPending(UUID user) {
        Instant now = Instant.now();
        Map<List<Object>, List<Long>> grouped = new LinkedHashMap<>();
        for (ChatMessage m : store.undelivered(user)) {
            m.markDelivered(now);
            grouped.computeIfAbsent(List.of(m.getConversationId(), m.getSenderId()), k -> new ArrayList<>()).add(m.getId());
        }
        return grouped.entrySet().stream()
                .map(e -> new DeliveryReceipt((UUID) e.getKey().get(0), (UUID) e.getKey().get(1), e.getValue())).toList();
    }

    // ---- reactions -------------------------------------------------------------------------------------------

    /** Toggle semantics: same emoji again removes it; a different emoji replaces the previous one. */
    @Transactional
    public ReactionUpdate react(UUID me, long messageId, String emoji) {
        if (emoji == null || emoji.isBlank() || emoji.length() > 16) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "emoji must be 1-16 characters");
        }
        ChatMessage m = store.findById(messageId).orElseThrow(this::messageNotFound);
        Conversation conv = requireMember(m.getConversationId(), me);
        var existing = reactions.findByMessageIdAndUserId(messageId, me);
        String result = emoji;
        if (existing.isPresent()) {
            if (existing.get().getEmoji().equals(emoji)) { reactions.delete(existing.get()); result = null; }
            else existing.get().changeTo(emoji);
        } else {
            reactions.save(new MessageReaction(messageId, me, emoji));
        }
        return new ReactionUpdate(messageId, conv.getId(), me, result, conv.peerOf(me));
    }

    @Transactional
    public ReactionUpdate removeReaction(UUID me, long messageId) {
        ChatMessage m = store.findById(messageId).orElseThrow(this::messageNotFound);
        Conversation conv = requireMember(m.getConversationId(), me);
        reactions.findByMessageIdAndUserId(messageId, me).ifPresent(reactions::delete);
        return new ReactionUpdate(messageId, conv.getId(), me, null, conv.peerOf(me));
    }

    // ---- translation -----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TranslationDto translate(UUID me, long messageId, String lang) {
        String target = lang == null ? "" : lang.toLowerCase(Locale.ROOT);
        if (!TRANSLATION_LANGS.contains(target)) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_LANGUAGE", "Supported: en, hi");
        }
        ChatMessage m = store.findById(messageId).orElseThrow(this::messageNotFound);
        requireMember(m.getConversationId(), me);
        if (m.getType() != MessageType.TEXT || m.getBody() == null) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_TRANSLATABLE", "Only text messages can be translated");
        }
        String text = translationCache.computeIfAbsent(messageId + ":" + target, k -> translator.translate(m.getBody(), target));
        return new TranslationDto(messageId, target, text);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    Conversation requireMember(UUID conversationId, UUID me) {
        // 404 (not 403) so outsiders cannot probe which conversation ids exist
        return conversations.findById(conversationId).filter(c -> c.hasMember(me))
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversation not found"));
    }

    private PjException messageNotFound() {
        return new PjException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "Message not found");
    }

    private void validateContent(MessageType type, SendCommand c) {
        String body = c.body();
        if (type == MessageType.TEXT) {
            if (body == null || body.isBlank()) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Message text is required");
        }
        if (body != null && body.length() > MAX_TEXT) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Message too long (max " + MAX_TEXT + ")");
        }
        if (type.isMedia()) {
            String url = c.mediaUrl();
            if (url == null || url.isBlank() || url.length() > 1000 || !(url.startsWith("https://") || url.startsWith("http://"))) {
                throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "A valid http(s) mediaUrl is required for " + type);
            }
            if (type == MessageType.VOICE_NOTE && (c.mediaDurationSec() == null || c.mediaDurationSec() < 1 || c.mediaDurationSec() > MAX_VOICE_SECONDS)) {
                throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Voice notes must be 1-" + MAX_VOICE_SECONDS + " seconds");
            }
        }
    }

    private ConversationDto view(UUID me, Conversation c, Map<UUID, Long> unread) {
        MessageDto last = c.getLastMessageId() == null ? null
                : store.findById(c.getLastMessageId()).map(m -> toDto(m, List.of())).orElse(null);
        UUID peer = c.peerOf(me);
        return new ConversationDto(c.getId(), peer, presence.isOnline(peer), unread.getOrDefault(c.getId(), 0L), last, c.getLastMessageAt());
    }

    private Map<UUID, Long> unreadMap(UUID me) { return store.unreadByConversation(me); }

    private Map<Long, List<MessageReaction>> reactionsFor(List<ChatMessage> messages) {
        if (messages.isEmpty()) return Map.of();
        return reactions.findByMessageIdIn(messages.stream().map(ChatMessage::getId).toList()).stream()
                .collect(Collectors.groupingBy(MessageReaction::getMessageId));
    }

    private static String trimToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    MessageDto toDto(ChatMessage m, List<MessageReaction> rs) {
        return new MessageDto(m.getId(), m.getConversationId(), m.getSenderId(), m.getRecipientId(), m.getType(),
                m.getBody(), m.getMediaUrl(), m.getMediaDurationSec(), m.getClientMsgId(), m.getStatus(), m.isFlagged(),
                m.getCreatedAt(), m.getDeliveredAt(), m.getReadAt(),
                rs.stream().map(r -> new ReactionDto(r.getUserId(), r.getEmoji())).toList());
    }
}
