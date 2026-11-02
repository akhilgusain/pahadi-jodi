package in.pahadijodi.media.adapter;

import in.pahadijodi.media.port.KycPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local stand-in for the KYC provider. PAN: structural validation only (an individual PAN is
 * AAAAA9999A with 4th letter 'P'). Aadhaar: DigiLocker-style consent flow; the demo code is 123456.
 * Replace with a licensed provider adapter (pj.kyc.provider=real) before launch.
 */
@Component
@ConditionalOnProperty(name = "pj.kyc.provider", havingValue = "fake", matchIfMissing = true)
public class FakeKycProvider implements KycPort {
    static final String DEMO_CODE = "123456";
    private static final Pattern PAN = Pattern.compile("^[A-Z]{3}P[A-Z][0-9]{4}[A-Z]$");

    @Override
    public PanResult verifyPan(String pan, String fullName) {
        String p = pan == null ? "" : pan.trim().toUpperCase(Locale.ROOT);
        if (!PAN.matcher(p).matches()) return new PanResult(false, null, "PAN_FORMAT_INVALID");
        if (p.startsWith("AAAAA") || p.substring(5, 9).equals("0000")) return new PanResult(false, null, "PAN_NOT_FOUND");
        return new PanResult(true, p, null);
    }

    @Override
    public AadhaarStart startAadhaar(UUID userId) {
        String ref = "fake-" + UUID.randomUUID();
        return new AadhaarStart(ref, "https://digilocker.example.invalid/consent?ref=" + ref);
    }

    @Override
    public AadhaarResult completeAadhaar(String reference, String code) {
        if (!DEMO_CODE.equals(code)) return new AadhaarResult(false, null, null, "CONSENT_NOT_GIVEN");
        byte[] h = sha256(reference);
        String last4 = String.format("%04d", Math.floorMod(((h[0] & 0xff) << 8) | (h[1] & 0xff), 10000));
        return new AadhaarResult(true, last4, HexFormat.of().formatHex(h), null);
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
