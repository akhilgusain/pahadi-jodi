package in.pahadijodi.search.service;

import java.text.Normalizer;
import java.util.Locale;

/** Case- and accent-insensitive comparison helpers (Latin diacritics only; Devanagari is left intact). */
public final class Text {
    private Text() {}

    public static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
