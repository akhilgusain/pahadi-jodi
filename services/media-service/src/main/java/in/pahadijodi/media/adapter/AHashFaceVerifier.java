package in.pahadijodi.media.adapter;

import in.pahadijodi.media.port.FaceVerifier;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic local stand-in for Rekognition / Azure Face. match() is a perceptual (average) hash
 * comparison of the two images, liveness() only checks that the image is decodable and big enough.
 * It proves the plumbing, thresholds and state machine; it does NOT recognise faces.
 */
@Component
@ConditionalOnProperty(name = "pj.face.provider", havingValue = "fake", matchIfMissing = true)
public class AHashFaceVerifier implements FaceVerifier {

    @Override
    public double liveness(byte[] selfie) {
        BufferedImage img = read(selfie);
        return img != null && Math.min(img.getWidth(), img.getHeight()) >= 200 ? 0.92 : 0.30;
    }

    @Override
    public double match(byte[] selfie, byte[] reference) {
        BufferedImage a = read(selfie), b = read(reference);
        if (a == null || b == null) return 0.0;
        long ha = hash(a), hb = hash(b);
        return 1.0 - Long.bitCount(ha ^ hb) / 64.0;
    }

    private static BufferedImage read(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            return null;
        }
    }

    /** 8x8 grid of mean luminance; bit set where the cell is brighter than the global mean. */
    static long hash(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double[] cell = new double[64];
        double total = 0;
        for (int cy = 0; cy < 8; cy++) {
            for (int cx = 0; cx < 8; cx++) {
                int x0 = cx * w / 8, x1 = Math.max(x0 + 1, (cx + 1) * w / 8);
                int y0 = cy * h / 8, y1 = Math.max(y0 + 1, (cy + 1) * h / 8);
                double sum = 0;
                int n = 0;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int rgb = img.getRGB(x, y);
                        sum += 0.299 * ((rgb >> 16) & 0xff) + 0.587 * ((rgb >> 8) & 0xff) + 0.114 * (rgb & 0xff);
                        n++;
                    }
                }
                cell[cy * 8 + cx] = sum / n;
                total += cell[cy * 8 + cx];
            }
        }
        double mean = total / 64;
        long bits = 0;
        for (int i = 0; i < 64; i++) if (cell[i] > mean) bits |= 1L << i;
        return bits;
    }
}
