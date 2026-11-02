package in.pahadijodi.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import in.pahadijodi.chat.config.ChatProperties;
import in.pahadijodi.chat.domain.*;
import in.pahadijodi.chat.port.ChatPolicyPort;
import in.pahadijodi.chat.port.EntitlementPort;
import in.pahadijodi.chat.service.Dtos.CallDto;
import in.pahadijodi.chat.service.Dtos.IceServer;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** WebRTC call lifecycle + signalling rules. SDP/ICE payloads are relayed opaquely over STOMP. */
@Service
public class CallService {
    static final int MAX_SIGNAL_CHARS = 16_384;

    public record SignalResult(UUID target, boolean ended, CallDto call) {}

    private final CallSessionRepository calls;
    private final ConversationRepository conversations;
    private final ChatPolicyPort policy;
    private final EntitlementPort entitlements;
    private final ChatProperties props;

    public CallService(CallSessionRepository calls, ConversationRepository conversations, ChatPolicyPort policy,
                       EntitlementPort entitlements, ChatProperties props) {
        this.calls = calls;
        this.conversations = conversations;
        this.policy = policy;
        this.entitlements = entitlements;
        this.props = props;
    }

    @Transactional
    public CallDto request(UUID me, UUID conversationId, CallMode mode, boolean recordingConsent) {
        Conversation conv = conversations.findById(conversationId).filter(c -> c.hasMember(me))
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversation not found"));
        UUID callee = conv.peerOf(me);
        var d = policy.check(me, callee);
        if (!d.allowed()) throw new PjException(HttpStatus.FORBIDDEN, d.code(), d.message());
        if (mode == CallMode.VIDEO && !entitlements.canVideoCall(me)) {
            throw new PjException(HttpStatus.FORBIDDEN, "PLAN_REQUIRED", "Video calling needs a Premium plan");
        }
        if (calls.findFirstByConversationIdAndStateIn(conversationId, EnumSet.of(CallState.REQUESTED, CallState.ACCEPTED)).isPresent()) {
            throw new PjException(HttpStatus.CONFLICT, "CALL_IN_PROGRESS", "A call is already in progress");
        }
        return dto(calls.save(new CallSession(conversationId, me, callee, mode == null ? CallMode.AUDIO : mode, recordingConsent)));
    }

    @Transactional
    public CallDto accept(UUID me, UUID callId, boolean recordingConsent) {
        CallSession c = load(callId, me);
        c.accept(me, recordingConsent);
        return dto(c);
    }

    @Transactional
    public CallDto decline(UUID me, UUID callId) {
        CallSession c = load(callId, me);
        c.decline(me);
        return dto(c);
    }

    @Transactional
    public CallDto end(UUID me, UUID callId) {
        CallSession c = load(callId, me);
        c.end(me);
        return dto(c);
    }

    @Transactional(readOnly = true)
    public CallDto get(UUID me, UUID callId) { return dto(load(callId, me)); }

    /** Validates a signalling message against the call state machine; hangup also ends the call. */
    @Transactional
    public SignalResult signal(UUID me, UUID callId, String kind, JsonNode payload) {
        CallSession c = load(callId, me);
        String k = kind == null ? "" : kind.toLowerCase();
        if (!c.getState().active()) throw new PjException(HttpStatus.CONFLICT, "INVALID_CALL_STATE", "Call is " + c.getState());
        if (payload != null && payload.toString().length() > MAX_SIGNAL_CHARS) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "SIGNAL_TOO_LARGE", "Signalling payload too large");
        }
        boolean caller = me.equals(c.getCallerId());
        switch (k) {
            case "offer" -> { if (!caller && c.getState() != CallState.ACCEPTED) throw badSignal("Callee may only offer after accepting"); }
            case "answer" -> {
                if (caller || c.getState() != CallState.ACCEPTED) throw badSignal("Only the callee may answer, after accepting");
            }
            case "ice" -> { /* either party while ringing or live */ }
            case "hangup" -> {
                c.end(me);
                return new SignalResult(c.otherParty(me), true, dto(c));
            }
            default -> throw badSignal("Unknown signal kind '" + kind + "' (offer|answer|ice|hangup)");
        }
        return new SignalResult(c.otherParty(me), false, dto(c));
    }

    /** Ring timeout sweep; the caller dispatches the returned calls. */
    @Transactional
    public List<CallDto> expireRinging() {
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(props.call().ringTimeoutSeconds()));
        List<CallDto> out = new ArrayList<>();
        for (CallSession c : calls.findByStateAndCreatedAtBefore(CallState.REQUESTED, cutoff)) {
            c.miss();
            out.add(dto(c));
        }
        return out;
    }

    /** STUN plus (when configured) TURN with coturn "REST API" ephemeral credentials: user = expiry:userId. */
    public List<IceServer> iceServers(UUID me) {
        List<IceServer> out = new ArrayList<>();
        var rtc = props.rtc();
        if (!rtc.stunUrls().isEmpty()) out.add(new IceServer(rtc.stunUrls(), null, null));
        if (!rtc.turnUrls().isEmpty() && !rtc.turnSecret().isBlank()) {
            String username = (Instant.now().getEpochSecond() + rtc.turnTtlSeconds()) + ":" + me;
            out.add(new IceServer(rtc.turnUrls(), username, hmacSha1Base64(rtc.turnSecret(), username)));
        }
        return out;
    }

    static String hmacSha1Base64(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private CallSession load(UUID callId, UUID me) {
        return calls.findById(callId).filter(c -> c.isParticipant(me))
                .orElseThrow(() -> new PjException(HttpStatus.NOT_FOUND, "CALL_NOT_FOUND", "Call not found"));
    }

    private static PjException badSignal(String m) { return new PjException(HttpStatus.CONFLICT, "INVALID_SIGNAL", m); }

    static CallDto dto(CallSession c) {
        return new CallDto(c.getId(), c.getConversationId(), c.getCallerId(), c.getCalleeId(), c.getMode(), c.getState(),
                c.recordingAllowed(), c.getCreatedAt(), c.getAnsweredAt(), c.getEndedAt());
    }
}
