package in.pahadijodi.media.port;

import java.util.UUID;

/**
 * Government-ID verification provider (DigiLocker / licensed KYC aggregator in production).
 * The Aadhaar number must never reach this service: the user consents at the provider and we
 * only receive the last 4 digits plus a stable provider token.
 */
public interface KycPort {
    record PanResult(boolean valid, String normalizedPan, String reason) {}
    PanResult verifyPan(String pan, String fullName);

    record AadhaarStart(String reference, String consentUrl) {}
    AadhaarStart startAadhaar(UUID userId);

    record AadhaarResult(boolean success, String last4, String uidToken, String reason) {}
    AadhaarResult completeAadhaar(String reference, String code);
}
