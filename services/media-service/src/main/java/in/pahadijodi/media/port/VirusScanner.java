package in.pahadijodi.media.port;

public interface VirusScanner {
    record ScanResult(boolean clean, String signature) {}
    ScanResult scan(byte[] data);
}
