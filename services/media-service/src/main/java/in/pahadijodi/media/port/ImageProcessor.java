package in.pahadijodi.media.port;

public interface ImageProcessor {
    record ProcessedImage(byte[] main, byte[] thumbnail, int width, int height) {}
    /** Decodes, strips metadata (re-encode), bounds size, builds a thumbnail. Throws IllegalArgumentException for bad images. */
    ProcessedImage process(byte[] raw);
}
