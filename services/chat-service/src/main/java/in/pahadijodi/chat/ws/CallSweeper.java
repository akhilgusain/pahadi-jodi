package in.pahadijodi.chat.ws;

import in.pahadijodi.chat.service.CallService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Marks unanswered calls MISSED after the ring timeout and tells both parties. */
@Component
public class CallSweeper {
    private final CallService calls;
    private final ChatDispatcher dispatcher;

    public CallSweeper(CallService calls, ChatDispatcher dispatcher) {
        this.calls = calls;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${pj.chat.call.sweep-interval-ms:10000}")
    public void sweep() {
        calls.expireRinging().forEach(c -> {
            dispatcher.call(c.callerId(), "missed", c);
            dispatcher.call(c.calleeId(), "missed", c);
        });
    }
}
