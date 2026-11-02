package in.pahadijodi.media.api;

import in.pahadijodi.common.security.ResourceServerAutoConfiguration;
import in.pahadijodi.media.port.KycPort;
import in.pahadijodi.media.service.VerificationService;
import in.pahadijodi.media.service.VerificationService.CheckView;
import in.pahadijodi.media.service.VerificationService.StatusView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/verification")
public class VerificationController {
    public record PanRequest(@NotBlank String pan, String fullName) {}
    public record AadhaarCompleteRequest(@NotBlank String reference, @NotBlank String code) {}
    public record SelfieRequest(@NotNull UUID selfieMediaId) {}
    public record ReviewDecision(@NotNull String decision, String reason) {}   // APPROVE | REJECT

    private final VerificationService service;

    public VerificationController(VerificationService service) { this.service = service; }

    private static UUID uid(Jwt jwt) { return ResourceServerAutoConfiguration.userId(jwt); }

    @GetMapping("/status")
    StatusView status(@AuthenticationPrincipal Jwt jwt) { return service.status(uid(jwt)); }

    @PostMapping("/pan")
    StatusView pan(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PanRequest r) {
        return service.submitPan(uid(jwt), r.pan(), r.fullName());
    }

    @PostMapping("/aadhaar/start")
    @ResponseStatus(HttpStatus.CREATED)
    KycPort.AadhaarStart aadhaarStart(@AuthenticationPrincipal Jwt jwt) { return service.startAadhaar(uid(jwt)); }

    @PostMapping("/aadhaar/complete")
    StatusView aadhaarComplete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AadhaarCompleteRequest r) {
        return service.completeAadhaar(uid(jwt), r.reference(), r.code());
    }

    @PostMapping("/selfie")
    StatusView selfie(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SelfieRequest r) {
        return service.verifySelfie(uid(jwt), r.selfieMediaId());
    }

    @GetMapping("/admin/queue")
    @PreAuthorize("hasAnyRole('ADMIN','VERIFIER')")
    List<CheckView> queue() { return service.reviewQueue(); }

    @PostMapping("/admin/{checkId}/decision")
    @PreAuthorize("hasAnyRole('ADMIN','VERIFIER')")
    StatusView decide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID checkId, @Valid @RequestBody ReviewDecision d) {
        return service.decide(uid(jwt), checkId, "APPROVE".equalsIgnoreCase(d.decision()), d.reason());
    }
}
