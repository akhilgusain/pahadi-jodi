package in.pahadijodi.media.service;

import in.pahadijodi.common.outbox.OutboxService;
import in.pahadijodi.common.web.ProblemAdvice.PjException;
import in.pahadijodi.media.domain.*;
import in.pahadijodi.media.port.BlobStore;
import in.pahadijodi.media.port.ImageProcessor;
import in.pahadijodi.media.port.VirusScanner;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private static final Map<MediaKind, Set<String>> ALLOWED_TYPES = Map.of(
            MediaKind.PHOTO, Set.of("image/jpeg", "image/png"),
            MediaKind.SELFIE, Set.of("image/jpeg", "image/png"),
            MediaKind.VIDEO, Set.of("video/mp4", "video/quicktime"),
            MediaKind.DOCUMENT, Set.of("application/pdf", "image/jpeg", "image/png"),
            MediaKind.VOICE, Set.of("audio/mpeg", "audio/mp4", "audio/ogg", "audio/webm"));
    private static final long MB = 1024 * 1024;
    private static final Map<MediaKind, Long> MAX_BYTES = Map.of(
            MediaKind.PHOTO, 10 * MB, MediaKind.SELFIE, 10 * MB, MediaKind.VIDEO, 100 * MB,
            MediaKind.DOCUMENT, 10 * MB, MediaKind.VOICE, 5 * MB);

    private final MediaAssetRepository assets;
    private final AccessGrantRepository grants;
    private final MatchLinkRepository matches;
    private final BlobStore blobs;
    private final VirusScanner scanner;
    private final ImageProcessor images;
    private final OutboxService outbox;
    private final UrlSigner signer;
    private final int maxPhotos;
    private final long urlTtl;

    public MediaService(MediaAssetRepository assets, AccessGrantRepository grants, MatchLinkRepository matches,
                        BlobStore blobs, VirusScanner scanner, ImageProcessor images, OutboxService outbox,
                        UrlSigner signer, @Value("${pj.media.max-photos}") int maxPhotos,
                        @Value("${pj.media.url-ttl-seconds}") long urlTtl) {
        this.assets = assets;
        this.grants = grants;
        this.matches = matches;
        this.blobs = blobs;
        this.scanner = scanner;
        this.images = images;
        this.outbox = outbox;
        this.signer = signer;
        this.maxPhotos = maxPhotos;
        this.urlTtl = urlTtl;
    }

    // ---- upload flow: declare -> PUT content -> complete ------------------------------------------

    @Transactional
    public MediaAsset declare(UUID userId, MediaKind kind, String contentType, long size) {
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.get(kind).contains(ct)) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_MEDIA_TYPE", kind + " does not accept " + contentType);
        }
        if (size <= 0 || size > MAX_BYTES.get(kind)) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_TOO_LARGE", "Max " + MAX_BYTES.get(kind) / MB + " MB for " + kind);
        }
        if (kind == MediaKind.PHOTO) {
            long n = assets.countByUserIdAndKindAndStatusIn(userId, kind,
                    List.of(MediaStatus.UPLOAD_PENDING, MediaStatus.UPLOADED, MediaStatus.SCANNING, MediaStatus.READY));
            if (n >= maxPhotos) throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "PHOTO_LIMIT", "At most " + maxPhotos + " photos");
        }
        return assets.save(new MediaAsset(userId, kind, ct, size, defaultPrivacy(kind)));
    }

    private static Privacy defaultPrivacy(MediaKind k) {
        return switch (k) {
            case PHOTO -> Privacy.PUBLIC;
            case VIDEO, VOICE -> Privacy.MATCHES_ONLY;
            case DOCUMENT, SELFIE -> Privacy.PRIVATE;
        };
    }

    @Transactional
    public MediaAsset receive(UUID userId, UUID id, byte[] data) {
        MediaAsset a = owned(userId, id);
        if (a.getStatus() != MediaStatus.UPLOAD_PENDING) throw invalidState(a);
        if (data.length != a.getDeclaredSize()) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "SIZE_MISMATCH", "Body size differs from declared size");
        }
        if (a.getContentType().equals("application/pdf") && !startsWith(data, "%PDF")) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "CONTENT_MISMATCH", "File is not a PDF");
        }
        String key = "raw/" + userId + "/" + id;
        blobs.put(key, data);
        a.uploaded(key, data.length);
        outbox.publish("pj.media.uploaded.v1", "MediaUploaded", userId.toString(),
                Map.of("mediaId", id.toString(), "userId", userId.toString(), "kind", a.getKind().name(),
                        "contentType", a.getContentType(), "sizeBytes", data.length));
        return a;
    }

    /** Synchronous MVP pipeline: scan, then (images) strip/resize/thumbnail. Video transcoding is deferred. */
    @Transactional
    public MediaAsset complete(UUID userId, UUID id) {
        MediaAsset a = owned(userId, id);
        if (a.getStatus() == MediaStatus.READY || a.getStatus() == MediaStatus.REJECTED) return a;   // idempotent
        if (a.getStatus() != MediaStatus.UPLOADED) throw invalidState(a);
        a.scanning();
        byte[] raw = blobs.get(a.getStorageKey());
        String rawKey = a.getStorageKey();

        var scan = scanner.scan(raw);
        if (!scan.clean()) {
            blobs.delete(rawKey);
            a.reject("MALWARE_DETECTED");
            log.warn("Malware {} rejected for media {} (user {})", scan.signature(), id, userId);
            return a;
        }
        if (a.getKind().isImage()) {
            ImageProcessor.ProcessedImage p;
            try {
                p = images.process(raw);
            } catch (IllegalArgumentException e) {
                blobs.delete(rawKey);
                a.reject("INVALID_IMAGE");
                return a;
            }
            String base = "media/" + userId + "/" + id;
            blobs.put(base + "/main.jpg", p.main());
            blobs.put(base + "/thumb.jpg", p.thumbnail());
            blobs.delete(rawKey);
            a.ready(base + "/main.jpg", base + "/thumb.jpg", "image/jpeg", p.main().length, p.width(), p.height());
        } else {
            a.ready(rawKey, null, a.getContentType(), raw.length, null, null);
        }
        if (a.getKind() == MediaKind.PHOTO) {
            List<MediaAsset> photos = assets.findByUserIdAndKindAndStatusOrderByPositionAscCreatedAtAsc(userId, MediaKind.PHOTO, MediaStatus.READY);
            a.setPosition(photos.stream().filter(x -> !x.getId().equals(id)).mapToInt(MediaAsset::getPosition).max().orElse(-1) + 1);
            boolean hasPrimary = photos.stream().anyMatch(x -> !x.getId().equals(id) && x.isPrimary());
            if (!hasPrimary) a.setPrimary(true);
        }
        outbox.publish("pj.media.ready.v1", "MediaReady", userId.toString(),
                Map.of("mediaId", id.toString(), "userId", userId.toString(), "kind", a.getKind().name(),
                        "primary", a.isPrimary()));
        return a;
    }

    // ---- gallery management ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MediaAsset> mine(UUID userId) {
        return assets.findByUserIdOrderByKindAscPositionAscCreatedAtAsc(userId);
    }

    @Transactional
    public MediaAsset makePrimary(UUID userId, UUID id) {
        MediaAsset a = owned(userId, id);
        if (a.getKind() != MediaKind.PHOTO || a.getStatus() != MediaStatus.READY) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_A_READY_PHOTO", "Only a ready photo can be primary");
        }
        assets.findByUserIdAndKindAndStatusOrderByPositionAscCreatedAtAsc(userId, MediaKind.PHOTO, MediaStatus.READY)
                .forEach(x -> x.setPrimary(x.getId().equals(id)));
        return a;
    }

    @Transactional
    public MediaAsset setPrivacy(UUID userId, UUID id, Privacy p) {
        MediaAsset a = owned(userId, id);
        if ((a.getKind() == MediaKind.DOCUMENT || a.getKind() == MediaKind.SELFIE) && p != Privacy.PRIVATE) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "PRIVACY_LOCKED", a.getKind() + " is always private");
        }
        if (p == Privacy.PRIVATE && a.isPrimary()) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "PRIMARY_MUST_BE_VISIBLE", "Pick another primary photo first");
        }
        a.setPrivacy(p);
        return a;
    }

    @Transactional
    public List<MediaAsset> reorder(UUID userId, List<UUID> ids) {
        List<MediaAsset> photos = assets.findByUserIdAndKindAndStatusOrderByPositionAscCreatedAtAsc(userId, MediaKind.PHOTO, MediaStatus.READY);
        Set<UUID> have = photos.stream().map(MediaAsset::getId).collect(Collectors.toSet());
        if (ids.size() != have.size() || !have.equals(new HashSet<>(ids))) {
            throw new PjException(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_MISMATCH", "ids must list every ready photo exactly once");
        }
        Map<UUID, MediaAsset> byId = photos.stream().collect(Collectors.toMap(MediaAsset::getId, x -> x));
        for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).setPosition(i);
        return ids.stream().map(byId::get).toList();
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        MediaAsset a = owned(userId, id);
        for (String k : new String[]{a.getStorageKey(), a.getThumbKey()}) if (k != null) blobs.delete(k);
        boolean wasPrimary = a.isPrimary();
        assets.delete(a);
        if (wasPrimary) {
            assets.findByUserIdAndKindAndStatusOrderByPositionAscCreatedAtAsc(userId, MediaKind.PHOTO, MediaStatus.READY)
                    .stream().filter(x -> !x.getId().equals(id)).findFirst().ifPresent(x -> x.setPrimary(true));
        }
    }

    // ---- reading ---------------------------------------------------------------------------------

    public record SignedUrl(String url, Instant expiresAt) {}

    @Transactional(readOnly = true)
    public SignedUrl urlFor(UUID viewer, Set<String> roles, UUID id, String variant) {
        MediaAsset a = assets.findById(id).orElseThrow(MediaService::notFound);
        if (a.getStatus() != MediaStatus.READY) throw notFound();
        if (!canView(viewer, roles, a)) {
            throw new PjException(HttpStatus.FORBIDDEN, "MEDIA_NOT_VISIBLE", "You do not have access to this media");
        }
        String v = "thumb".equals(variant) && a.getThumbKey() != null ? "thumb" : "orig";
        Instant exp = Instant.now().plus(Duration.ofSeconds(urlTtl));
        return new SignedUrl(signer.url(id, v, exp), exp);
    }

    boolean canView(UUID viewer, Set<String> roles, MediaAsset a) {
        if (a.getUserId().equals(viewer)) return true;
        if (a.getKind() == MediaKind.DOCUMENT || a.getKind() == MediaKind.SELFIE) {
            return roles.contains("ROLE_ADMIN") || roles.contains("ROLE_VERIFIER");
        }
        boolean matched = matches.existsByUserAAndUserB(viewer, a.getUserId());
        return switch (a.getPrivacy()) {
            case PUBLIC -> true;
            case MATCHES_ONLY -> matched;
            case ON_REQUEST -> matched || grants.findByOwnerIdAndViewerId(a.getUserId(), viewer)
                    .map(g -> g.getStatus() == AccessGrant.Status.GRANTED).orElse(false);
            case PRIVATE -> false;
        };
    }

    /** Called by the signed-URL controller after the signature has been verified. */
    @Transactional(readOnly = true)
    public Download download(UUID id, String variant) {
        MediaAsset a = assets.findById(id).orElseThrow(MediaService::notFound);
        if (a.getStatus() != MediaStatus.READY) throw notFound();
        String key = "thumb".equals(variant) && a.getThumbKey() != null ? a.getThumbKey() : a.getStorageKey();
        return new Download(blobs.get(key), a.getContentType());
    }

    public record Download(byte[] data, String contentType) {}

    /** Bytes of a user's READY asset (used by the verification flow). */
    @Transactional(readOnly = true)
    public byte[] bytesOf(MediaAsset a) {
        return blobs.get(a.getStorageKey());
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private MediaAsset owned(UUID userId, UUID id) {
        MediaAsset a = assets.findById(id).orElseThrow(MediaService::notFound);
        if (!a.getUserId().equals(userId)) throw notFound();   // do not reveal other users' ids
        return a;
    }

    private static PjException notFound() {
        return new PjException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found");
    }

    private static PjException invalidState(MediaAsset a) {
        return new PjException(HttpStatus.CONFLICT, "INVALID_STATE", "Media is " + a.getStatus());
    }

    private static boolean startsWith(byte[] d, String prefix) {
        byte[] p = prefix.getBytes(StandardCharsets.US_ASCII);
        if (d.length < p.length) return false;
        for (int i = 0; i < p.length; i++) if (d[i] != p[i]) return false;
        return true;
    }
}
