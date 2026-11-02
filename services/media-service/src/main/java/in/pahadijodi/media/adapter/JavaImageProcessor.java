package in.pahadijodi.media.adapter;

import in.pahadijodi.media.port.ImageProcessor;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/** Real (not fake) pipeline: re-encoding as JPEG drops EXIF/GPS and any appended payload. */
@Component
public class JavaImageProcessor implements ImageProcessor {
    static final int MAX_EDGE = 2048;
    static final int THUMB_EDGE = 320;
    static final long MAX_PIXELS = 40_000_000L;   // decompression-bomb guard

    @Override
    public ProcessedImage process(byte[] raw) {
        BufferedImage src = decode(raw);
        BufferedImage rgb = toRgb(src);
        BufferedImage main = scaleDown(rgb, MAX_EDGE);
        BufferedImage thumb = scaleDown(rgb, THUMB_EDGE);
        return new ProcessedImage(jpeg(main), jpeg(thumb), main.getWidth(), main.getHeight());
    }

    private static BufferedImage decode(byte[] raw) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IllegalArgumentException("Unsupported or corrupt image");
            ImageReader r = readers.next();
            try {
                r.setInput(in);
                if ((long) r.getWidth(0) * r.getHeight(0) > MAX_PIXELS) throw new IllegalArgumentException("Image too large");
                return r.read(0);
            } finally {
                r.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unsupported or corrupt image");
        }
    }

    private static BufferedImage toRgb(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);                      // flatten transparency onto white
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage scaleDown(BufferedImage src, int maxEdge) {
        int w = src.getWidth(), h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxEdge) return src;
        double f = (double) maxEdge / longest;
        int nw = Math.max(1, (int) Math.round(w * f)), nh = Math.max(1, (int) Math.round(h * f));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static byte[] jpeg(BufferedImage img) {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bos)) {
            ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.88f);
            w.setOutput(out);
            w.write(null, new IIOImage(img, null, null), p);   // null metadata: nothing carried over
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            w.dispose();
        }
    }
}
