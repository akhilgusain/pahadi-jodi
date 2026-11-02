package in.pahadijodi.notification.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live in-app push: one SSE stream per open browser tab. Single-instance: with several pods, fan out via Redis
 * pub/sub (or consume the same Kafka topic per pod) so the pod holding the connection receives the event.
 */
@Component
public class SseHub {
    private static final Logger log = LoggerFactory.getLogger(SseHub.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("ready").data("ok"));   // lets EventSource know the stream is live
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationCreated e) {
        List<SseEmitter> list = emitters.get(e.userId());
        if (list == null) return;
        for (SseEmitter em : list) {
            try {
                em.send(SseEmitter.event().id(e.notification().id().toString()).name("notification").data(e.notification()));
            } catch (IOException | IllegalStateException ex) {
                remove(e.userId(), em);
            }
        }
    }

    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        emitters.forEach((user, list) -> list.forEach(em -> {
            try { em.send(SseEmitter.event().comment("hb")); } catch (IOException | IllegalStateException ex) { remove(user, em); }
        }));
    }

    public int connections(UUID userId) { return emitters.getOrDefault(userId, List.of()).size(); }

    private void remove(UUID user, SseEmitter em) {
        emitters.computeIfPresent(user, (k, list) -> { list.remove(em); return list.isEmpty() ? null : list; });
        log.debug("SSE emitter removed for {}", user);
    }
}
