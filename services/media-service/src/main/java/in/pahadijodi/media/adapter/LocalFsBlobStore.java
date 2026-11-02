package in.pahadijodi.media.adapter;

import in.pahadijodi.media.port.BlobStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pj.media.provider", havingValue = "fake", matchIfMissing = true)
public class LocalFsBlobStore implements BlobStore {
    private final Path root;

    public LocalFsBlobStore(@Value("${pj.media.storage-dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Illegal blob key");   // path traversal guard
        return p;
    }

    @Override
    public void put(String key, byte[] data) {
        try {
            Path p = resolve(key);
            Files.createDirectories(p.getParent());
            Files.write(p, data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (NoSuchFileException e) {
            throw new IllegalStateException("Blob not found: " + key);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
