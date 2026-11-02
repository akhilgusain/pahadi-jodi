package in.pahadijodi.media.api;

import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.media.service.MediaService;
import in.pahadijodi.media.service.UrlSigner;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public by design: authorisation is the HMAC signature (issued only after a privacy check). */
@RestController
public class FileController {
    private final MediaService media;
    private final UrlSigner signer;

    public FileController(MediaService media, UrlSigner signer) {
        this.media = media;
        this.signer = signer;
    }

    @GetMapping("/api/v1/media/files/{id}")
    ResponseEntity<byte[]> file(@PathVariable UUID id, @RequestParam("v") String variant,
                                @RequestParam long exp, @RequestParam String sig) {
        if (!signer.verify(id, variant, exp, sig)) {
            throw new PjException(HttpStatus.FORBIDDEN, "INVALID_SIGNATURE", "Link expired or invalid");
        }
        var d = media.download(id, variant);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(d.data());
    }
}
