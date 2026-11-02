package in.pahadijodi.media.port;

public interface FaceVerifier {
    /** Probability (0..1) that the selfie is a live capture. */
    double liveness(byte[] selfie);
    /** Similarity (0..1) between two faces. */
    double match(byte[] selfie, byte[] reference);
}
