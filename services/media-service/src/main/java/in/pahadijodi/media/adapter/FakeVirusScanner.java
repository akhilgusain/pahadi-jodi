package in.pahadijodi.media.adapter;

import in.pahadijodi.media.port.VirusScanner;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Detects the standard EICAR test string. Production: ClamAV daemon / GuardDuty Malware Protection for S3. */
@Component
@ConditionalOnProperty(name = "pj.scan.provider", havingValue = "fake", matchIfMissing = true)
public class FakeVirusScanner implements VirusScanner {
    private static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @Override
    public ScanResult scan(byte[] data) {
        String s = new String(data, StandardCharsets.ISO_8859_1);
        return s.contains(EICAR) ? new ScanResult(false, "Eicar-Test-Signature") : new ScanResult(true, null);
    }
}
