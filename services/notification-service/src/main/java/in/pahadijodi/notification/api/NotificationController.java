package in.pahadijodi.notification.api;

import in.pahadijodi.common.security.ResourceServerAutoConfiguration;
import in.pahadijodi.notification.domain.Channel;
import in.pahadijodi.notification.domain.DeliveryLog;
import in.pahadijodi.notification.domain.DeliveryStatus;
import in.pahadijodi.notification.domain.Platform;
import in.pahadijodi.notification.service.DeviceService;
import in.pahadijodi.notification.service.NotificationDto;
import in.pahadijodi.notification.service.NotificationService;
import in.pahadijodi.notification.service.PreferenceService;
import in.pahadijodi.notification.service.PreferenceService.Prefs;
import in.pahadijodi.notification.service.SseHub;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    public record PageResponse<T>(List<T> items, int page, int size, long total, boolean hasMore) {}
    public record UnreadCount(long unread) {}
    public record PreferencesDto(boolean inApp, boolean email, boolean sms, boolean push, boolean whatsapp,
                                 Integer quietStart, Integer quietEnd, @NotBlank String locale) {}
    public record DeviceRequest(@NotBlank @Size(max = 512) String token, @NotNull Platform platform) {}
    public record DeliveryDto(Channel channel, DeliveryStatus status, int attempts, String suppressedReason, String lastError,
                              String providerRef, Instant nextAttemptAt, Instant createdAt, Instant sentAt) {}

    private final NotificationService service;
    private final PreferenceService preferences;
    private final DeviceService devices;
    private final SseHub hub;

    public NotificationController(NotificationService service, PreferenceService preferences, DeviceService devices, SseHub hub) {
        this.service = service;
        this.preferences = preferences;
        this.devices = devices;
        this.hub = hub;
    }

    @GetMapping
    PageResponse<NotificationDto> inbox(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "false") boolean unreadOnly) {
        var p = service.inbox(id(jwt), page, size, unreadOnly);
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.hasNext());
    }

    @GetMapping("/unread-count")
    UnreadCount unread(@AuthenticationPrincipal Jwt jwt) { return new UnreadCount(service.unreadCount(id(jwt))); }

    @PostMapping("/{id}/read")
    NotificationDto read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return service.markRead(id(jwt), id); }

    @PostMapping("/read-all")
    Map<String, Integer> readAll(@AuthenticationPrincipal Jwt jwt) { return Map.of("updated", service.markAllRead(id(jwt))); }

    @GetMapping("/preferences")
    Prefs prefs(@AuthenticationPrincipal Jwt jwt) { return preferences.get(id(jwt)); }

    @PutMapping("/preferences")
    Prefs updatePrefs(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferencesDto p) {
        return preferences.update(id(jwt), new Prefs(p.inApp(), p.email(), p.sms(), p.push(), p.whatsapp(), p.quietStart(), p.quietEnd(), p.locale()));
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void registerDevice(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeviceRequest r) {
        devices.register(id(jwt), r.token(), r.platform());
    }

    /** token as a query parameter: FCM/APNs tokens contain characters that are awkward in a path segment. */
    @DeleteMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unregisterDevice(@AuthenticationPrincipal Jwt jwt, @RequestParam String token) { devices.unregister(id(jwt), token); }

    /** Own recent delivery attempts per channel (what was sent / suppressed / retried). Handy for support and demos. */
    @GetMapping("/deliveries")
    List<DeliveryDto> deliveries(@AuthenticationPrincipal Jwt jwt) {
        return service.recentDeliveries(id(jwt)).stream().map(NotificationController::toDto).toList();
    }

    /** Server-sent events: "ready" on connect, then "notification" events; heartbeat comment every 25 s. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@AuthenticationPrincipal Jwt jwt) { return hub.register(id(jwt)); }

    private static DeliveryDto toDto(DeliveryLog d) {
        return new DeliveryDto(d.getChannel(), d.getStatus(), d.getAttempts(), d.getSuppressedReason(), d.getLastError(),
                d.getProviderRef(), d.getNextAttemptAt(), d.getCreatedAt(), d.getSentAt());
    }

    private static UUID id(Jwt jwt) { return ResourceServerAutoConfiguration.userId(jwt); }
}
