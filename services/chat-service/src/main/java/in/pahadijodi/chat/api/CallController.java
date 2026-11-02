package in.pahadijodi.chat.api;

import in.pahadijodi.chat.domain.CallMode;
import in.pahadijodi.chat.service.CallService;
import in.pahadijodi.chat.service.Dtos.CallDto;
import in.pahadijodi.chat.service.Dtos.IceServer;
import in.pahadijodi.chat.ws.ChatDispatcher;
import in.pahadijodi.common.security.ResourceServerAutoConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Call lifecycle (consent state machine). SDP/ICE exchange itself goes over STOMP /app/call.signal.
 * Server pushes: CALL {event: incoming|accepted|declined|cancelled|ended|missed, call} and CALL_SIGNAL.
 * Recording: the call carries recordingAllowed (true only if BOTH parties consented). Actual media
 * recording is not implemented; it needs an SFU/recorder (phase 2 in docs/03).
 */
@RestController
@RequestMapping("/api/v1/chat/calls")
public class CallController {
    public record StartCallRequest(@NotNull UUID conversationId, CallMode mode, boolean recordingConsent) {}
    public record AcceptRequest(boolean recordingConsent) {}
    public record IceServersResponse(List<IceServer> iceServers) {}

    private final CallService calls;
    private final ChatDispatcher dispatcher;

    public CallController(CallService calls, ChatDispatcher dispatcher) {
        this.calls = calls;
        this.dispatcher = dispatcher;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CallDto start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartCallRequest r) {
        CallDto c = calls.request(id(jwt), r.conversationId(), r.mode(), r.recordingConsent());
        dispatcher.call(c.calleeId(), "incoming", c);
        return c;
    }

    @PostMapping("/{callId}/accept")
    CallDto accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID callId, @RequestBody(required = false) AcceptRequest r) {
        CallDto c = calls.accept(id(jwt), callId, r != null && r.recordingConsent());
        dispatcher.call(c.callerId(), "accepted", c);
        return c;
    }

    @PostMapping("/{callId}/decline")
    CallDto decline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID callId) {
        CallDto c = calls.decline(id(jwt), callId);
        dispatcher.call(c.callerId(), "declined", c);
        return c;
    }

    @PostMapping("/{callId}/end")
    CallDto end(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID callId) {
        UUID me = id(jwt);
        CallDto c = calls.end(me, callId);
        String event = c.state().name().equals("CANCELLED") ? "cancelled" : "ended";
        dispatcher.call(me.equals(c.callerId()) ? c.calleeId() : c.callerId(), event, c);
        return c;
    }

    @GetMapping("/{callId}")
    CallDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID callId) { return calls.get(id(jwt), callId); }

    @GetMapping("/ice-servers")
    IceServersResponse ice(@AuthenticationPrincipal Jwt jwt) { return new IceServersResponse(calls.iceServers(id(jwt))); }

    private static UUID id(Jwt jwt) { return ResourceServerAutoConfiguration.userId(jwt); }
}
