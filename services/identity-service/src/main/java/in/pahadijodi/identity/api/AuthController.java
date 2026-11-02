package in.pahadijodi.identity.api;

import in.pahadijodi.identity.service.AuthService;
import in.pahadijodi.identity.service.AuthService.Tokens;
import in.pahadijodi.identity.service.TokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
    public record RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min = 10, max = 128) String password) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}

    private final AuthService auth;
    private final TokenService tokens;

    public AuthController(AuthService auth, TokenService tokens) {
        this.auth = auth;
        this.tokens = tokens;
    }

    @PostMapping("/api/v1/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    Tokens register(@Valid @RequestBody RegisterRequest r) { return auth.register(r.email(), r.password()); }

    @PostMapping("/api/v1/auth/login")
    Tokens login(@Valid @RequestBody LoginRequest r) { return auth.login(r.email(), r.password()); }

    @PostMapping("/api/v1/auth/refresh")
    Tokens refresh(@Valid @RequestBody RefreshRequest r) { return auth.refresh(r.refreshToken()); }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() { return tokens.publicJwks(); }
}
