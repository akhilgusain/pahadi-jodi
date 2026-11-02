package in.pahadijodi.fraud.service;

import in.pahadijodi.fraud.domain.PhotoHash;
import in.pahadijodi.fraud.domain.Repositories.PhotoHashRepository;
import in.pahadijodi.fraud.text.Simhash;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** pj.fraud.photo.provider=fake (default): compares perceptual hashes stored in our own DB using banded lookup. */
@Component
@ConditionalOnProperty(name = "pj.fraud.photo.provider", havingValue = "fake", matchIfMissing = true)
public class LocalPhotoDuplicateDetector implements PhotoDuplicateDetector {
    private final PhotoHashRepository repo;
    private final int maxHamming;

    public LocalPhotoDuplicateDetector(PhotoHashRepository repo, @Value("${pj.fraud.photo.max-hamming:4}") int maxHamming) {
        this.repo = repo;
        this.maxHamming = maxHamming;
    }

    @Override
    public void register(UUID userId, long perceptualHash) {
        if (repo.findByUserId(userId).stream().noneMatch(p -> p.getPhash() == perceptualHash)) {
            repo.save(new PhotoHash(userId, perceptualHash));
        }
    }

    @Override
    public boolean hasDuplicate(UUID userId) {
        for (PhotoHash mine : repo.findByUserId(userId)) {
            long h = mine.getPhash();
            // 64 bits in 4 bands: distance <= 3 guarantees a matching band; for 4 we also widen via exact scan of candidates
            var candidates = repo.candidates(userId, Simhash.band(h, 0), Simhash.band(h, 1), Simhash.band(h, 2), Simhash.band(h, 3));
            if (candidates.stream().anyMatch(c -> Simhash.hamming(h, c.getPhash()) <= maxHamming)) return true;
        }
        return false;
    }
}
