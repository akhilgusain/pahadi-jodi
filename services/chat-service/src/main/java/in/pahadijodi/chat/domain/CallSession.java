package in.pahadijodi.chat.domain;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Call consent state machine. Recording is allowed only when BOTH parties consented. */
@Entity
@Table(name = "call_session")
public class CallSession {
    @Id private UUID id;
    @Column(name = "conversation_id", nullable = false) private UUID conversationId;
    @Column(name = "caller_id", nullable = false) private UUID callerId;
    @Column(name = "callee_id", nullable = false) private UUID calleeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CallMode mode;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CallState state;
    @Column(name = "caller_recording_consent", nullable = false) private boolean callerRecordingConsent;
    @Column(name = "callee_recording_consent", nullable = false) private boolean calleeRecordingConsent;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "answered_at") private Instant answeredAt;
    @Column(name = "ended_at") private Instant endedAt;

    protected CallSession() {}

    public CallSession(UUID conversationId, UUID callerId, UUID calleeId, CallMode mode, boolean callerConsent) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.mode = mode;
        this.state = CallState.REQUESTED;
        this.callerRecordingConsent = callerConsent;
        this.createdAt = Instant.now();
    }

    public boolean recordingAllowed() { return callerRecordingConsent && calleeRecordingConsent; }
    public boolean isParticipant(UUID u) { return callerId.equals(u) || calleeId.equals(u); }
    public UUID otherParty(UUID u) { return callerId.equals(u) ? calleeId : callerId; }

    public void accept(UUID by, boolean recordingConsent) {
        requireState(by.equals(calleeId), CallState.REQUESTED, "Only the callee can accept a ringing call");
        state = CallState.ACCEPTED; calleeRecordingConsent = recordingConsent; answeredAt = Instant.now();
    }
    public void decline(UUID by) {
        requireState(by.equals(calleeId), CallState.REQUESTED, "Only the callee can decline a ringing call");
        state = CallState.DECLINED; endedAt = Instant.now();
    }
    /** Caller hanging up while ringing = CANCELLED; either party during a live call = ENDED. */
    public void end(UUID by) {
        if (!isParticipant(by)) throw new PjException(HttpStatus.FORBIDDEN, "NOT_A_PARTICIPANT", "Not part of this call");
        if (state == CallState.REQUESTED && by.equals(callerId)) { state = CallState.CANCELLED; endedAt = Instant.now(); return; }
        if (state == CallState.ACCEPTED) { state = CallState.ENDED; endedAt = Instant.now(); return; }
        throw new PjException(HttpStatus.CONFLICT, "INVALID_CALL_STATE", "Call cannot be ended from state " + state);
    }
    public void miss() {
        if (state == CallState.REQUESTED) { state = CallState.MISSED; endedAt = Instant.now(); }
    }

    private void requireState(boolean rightActor, CallState expected, String actorMessage) {
        if (!rightActor) throw new PjException(HttpStatus.FORBIDDEN, "NOT_CALLEE", actorMessage);
        if (state != expected) throw new PjException(HttpStatus.CONFLICT, "INVALID_CALL_STATE", "Call is " + state);
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public UUID getCallerId() { return callerId; }
    public UUID getCalleeId() { return calleeId; }
    public CallMode getMode() { return mode; }
    public CallState getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAnsweredAt() { return answeredAt; }
    public Instant getEndedAt() { return endedAt; }
}
