package in.pahadijodi.media.api;

import in.pahadijodi.media.domain.MediaAsset;
import in.pahadijodi.media.domain.MediaKind;
import in.pahadijodi.media.domain.Privacy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MediaDtos {
    private MediaDtos() {}

    public record DeclareRequest(@NotNull MediaKind kind, @NotBlank String contentType, @Positive long sizeBytes) {}
    public record DeclareResponse(UUID id, String method, String uploadUrl, String completeUrl) {}
    public record PrivacyRequest(@NotNull Privacy privacy) {}
    public record OrderRequest(@NotNull List<UUID> ids) {}
    public record AccessRequestBody(@NotNull UUID ownerId) {}
    public record AccessDecision(@NotNull String decision) {}   // GRANT | DENY

    public record AssetResponse(UUID id, MediaKind kind, String status, Privacy privacy, boolean primary, int position,
                                String contentType, long sizeBytes, Integer width, Integer height,
                                String rejectReason, Instant createdAt) {
        public static AssetResponse from(MediaAsset a) {
            return new AssetResponse(a.getId(), a.getKind(), a.getStatus().name(), a.getPrivacy(), a.isPrimary(),
                    a.getPosition(), a.getContentType(), a.getSizeBytes(), a.getWidth(), a.getHeight(),
                    a.getRejectReason(), a.getCreatedAt());
        }
    }
}
