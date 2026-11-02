package in.pahadijodi.user.api;

import in.pahadijodi.user.domain.Consent;
import in.pahadijodi.user.domain.DataRequest;
import in.pahadijodi.user.domain.Device;
import in.pahadijodi.user.service.UserService;
import in.pahadijodi.user.service.UserService.Me;
import in.pahadijodi.user.service.UserService.PreferenceView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {
    public record ContactRequest(@Email @Size(max = 320) String email,
                                 @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "must be E.164, e.g. +919876543210") String phone) {}
    public record PreferenceRequest(
            @NotNull @Pattern(regexp = "en|hi") String language,
            boolean notifyEmail, boolean notifySms, boolean notifyPush, boolean notifyWhatsapp,
            @NotNull @Pattern(regexp = "ALL_MEMBERS|VERIFIED_ONLY|CONTACTS_ONLY") String profileVisibility,
            @NotNull @Pattern(regexp = "NONE|PREMIUM|ALL") String allowDirectMessages) {}
    public record ConsentRequest(@NotNull Consent.Type type, @NotBlank @Size(max = 20) String version, boolean granted) {}
    public record ConsentView(Consent.Type type, String version, boolean granted, Instant recordedAt) {
        static ConsentView of(Consent c) { return new ConsentView(c.getType(), c.getVersion(), c.isGranted(), c.getRecordedAt()); }
    }
    public record DeviceRequest(@NotBlank @Size(max = 120) String name, @NotNull @Pattern(regexp = "WEB|ANDROID|IOS") String platform) {}
    public record DeviceView(UUID id, String name, String platform, Instant lastSeen) {
        static DeviceView of(Device d) { return new DeviceView(d.getId(), d.getName(), d.getPlatform(), d.getLastSeen()); }
    }
    public record DataRequestView(UUID id, DataRequest.Type type, DataRequest.Status status, Instant requestedAt,
                                  Instant scheduledFor, Instant completedAt) {
        static DataRequestView of(DataRequest r) {
            return new DataRequestView(r.getId(), r.getType(), r.getStatus(), r.getRequestedAt(), r.getScheduledFor(), r.getCompletedAt());
        }
    }

    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    private static UUID id(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    @GetMapping
    Me me(@AuthenticationPrincipal Jwt jwt) { return service.me(id(jwt)); }

    @PutMapping("/contact")
    Me contact(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ContactRequest r) {
        return service.updateContact(id(jwt), r.email(), r.phone());
    }

    @PutMapping("/preferences")
    Me preferences(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferenceRequest r) {
        return service.updatePreferences(id(jwt), new PreferenceView(r.language(), r.notifyEmail(), r.notifySms(),
                r.notifyPush(), r.notifyWhatsapp(), r.profileVisibility(), r.allowDirectMessages()));
    }

    @GetMapping("/consents")
    List<ConsentView> consents(@AuthenticationPrincipal Jwt jwt) {
        return service.currentConsents(id(jwt)).stream().map(ConsentView::of).toList();
    }

    @PutMapping("/consents")
    List<ConsentView> consent(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsentRequest r) {
        return service.recordConsent(id(jwt), r.type(), r.version(), r.granted()).stream().map(ConsentView::of).toList();
    }

    @GetMapping("/devices")
    List<DeviceView> devices(@AuthenticationPrincipal Jwt jwt) {
        return service.listDevices(id(jwt)).stream().map(DeviceView::of).toList();
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    DeviceView registerDevice(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeviceRequest r) {
        return DeviceView.of(service.registerDevice(id(jwt), r.name(), r.platform()));
    }

    @DeleteMapping("/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeDevice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID deviceId) {
        service.revokeDevice(id(jwt), deviceId);
    }

    @PostMapping("/deactivate")
    Me deactivate(@AuthenticationPrincipal Jwt jwt) { return service.deactivate(id(jwt)); }

    @PostMapping("/reactivate")
    Me reactivate(@AuthenticationPrincipal Jwt jwt) { return service.reactivate(id(jwt)); }

    @GetMapping("/data-requests")
    List<DataRequestView> dataRequests(@AuthenticationPrincipal Jwt jwt) {
        return service.listRequests(id(jwt)).stream().map(DataRequestView::of).toList();
    }

    @PostMapping("/data-requests/export")
    @ResponseStatus(HttpStatus.ACCEPTED)
    DataRequestView export(@AuthenticationPrincipal Jwt jwt) { return DataRequestView.of(service.requestExport(id(jwt))); }

    @PostMapping("/data-requests/erasure")
    @ResponseStatus(HttpStatus.ACCEPTED)
    DataRequestView erasure(@AuthenticationPrincipal Jwt jwt) { return DataRequestView.of(service.requestErasure(id(jwt))); }

    @DeleteMapping("/data-requests/erasure")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelErasure(@AuthenticationPrincipal Jwt jwt) { service.cancelErasure(id(jwt)); }
}
