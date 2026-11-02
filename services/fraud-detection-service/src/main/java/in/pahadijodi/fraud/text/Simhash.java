package in.pahadijodi.fraud.text;

import java.util.Locale;

/** 64-bit simhash over word shingles plus banding helpers for near-duplicate lookup. */
public final class Simhash {
    private Simhash() {}

    public static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    public static int tokenCount(String text) {
        String n = normalize(text);
        return n.isEmpty() ? 0 : n.split(" ").length;
    }

    public static long hash(String text) {
        String[] words = normalize(text).split(" ");
        int[] v = new int[64];
        int shingle = words.length >= 3 ? 3 : 1;
        for (int i = 0; i + shingle <= words.length; i++) {
            long h = fnv1a(String.join(" ", java.util.Arrays.copyOfRange(words, i, i + shingle)));
            for (int b = 0; b < 64; b++) v[b] += ((h >>> b) & 1L) == 1L ? 1 : -1;
        }
        long out = 0;
        for (int b = 0; b < 64; b++) if (v[b] > 0) out |= 1L << b;
        return out;
    }

    public static int hamming(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /** Four 16-bit bands: two hashes within hamming distance <= 3 must agree on at least one band (pigeonhole). */
    public static int band(long hash, int i) {
        return (int) ((hash >>> (16 * i)) & 0xFFFFL);
    }

    private static long fnv1a(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            h ^= (b & 0xffL);
            h *= 0x100000001b3L;
        }
        // extra avalanche so short shingles spread over all 64 bits
        h ^= (h >>> 33); h *= 0xff51afd7ed558ccdL; h ^= (h >>> 33);
        return h;
    }
}
