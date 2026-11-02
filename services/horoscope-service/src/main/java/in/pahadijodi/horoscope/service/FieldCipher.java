package in.pahadijodi.horoscope.service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Application-layer AES-256-GCM for birth data (docs/04 section 14.10). The key comes from PJ_HOROSCOPE_DATA_KEY
 * (base64, 32 bytes), in production sourced from KMS/Secrets Manager. Without a key, values are stored with a
 * "plain:" prefix and a loud warning, unless pj.horoscope.require-encryption=true, which fails startup.
 * Rotating the key requires re-encrypting rows: the "enc1:" prefix leaves room for a key-id scheme.
 */
@Component
public class FieldCipher {
    private static final Logger log = LoggerFactory.getLogger(FieldCipher.class);
    private static final SecureRandom RNG = new SecureRandom();
    private final SecretKeySpec key;

    public FieldCipher(@Value("${pj.horoscope.data-key:}") String b64Key,
                       @Value("${pj.horoscope.require-encryption:false}") boolean required) {
        if (b64Key == null || b64Key.isBlank()) {
            if (required) throw new IllegalStateException("PJ_HOROSCOPE_DATA_KEY is required (pj.horoscope.require-encryption=true)");
            this.key = null;
        } else {
            byte[] raw = Base64.getDecoder().decode(b64Key.trim());
            if (raw.length != 32) throw new IllegalStateException("PJ_HOROSCOPE_DATA_KEY must be 32 bytes (base64)");
            this.key = new SecretKeySpec(raw, "AES");
        }
    }

    @PostConstruct
    void warn() {
        if (key == null) log.warn("PJ_HOROSCOPE_DATA_KEY not set: birth data is stored UNENCRYPTED (dev only)");
    }

    public String encrypt(String plain) {
        if (key == null) return "plain:" + plain;
        try {
            byte[] iv = new byte[12];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return "enc1:" + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored.startsWith("plain:")) return stored.substring(6);
        if (!stored.startsWith("enc1:")) throw new IllegalStateException("unknown ciphertext format");
        if (key == null) throw new IllegalStateException("data key missing: cannot decrypt");
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(5));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, in, 0, 12));
            return new String(c.doFinal(in, 12, in.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decryption failed", e);
        }
    }
}
