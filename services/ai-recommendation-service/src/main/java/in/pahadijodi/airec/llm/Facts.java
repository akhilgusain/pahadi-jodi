package in.pahadijodi.airec.llm;

import java.util.List;
import java.util.Map;

/** Null-safe accessors for the loosely typed facts map. */
public final class Facts {
    private Facts() {}

    public static String str(Map<String, Object> f, String key) {
        Object v = f.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }

    public static String str(Map<String, Object> f, String key, String dflt) {
        String v = str(f, key);
        return v == null ? dflt : v;
    }

    public static int num(Map<String, Object> f, String key, int dflt) {
        Object v = f.get(key);
        return v instanceof Number n ? n.intValue() : dflt;
    }

    @SuppressWarnings("unchecked")
    public static List<String> list(Map<String, Object> f, String key) {
        Object v = f.get(key);
        return v instanceof List<?> l ? (List<String>) l : List.of();
    }

    public static boolean hi(Map<String, Object> f) { return "hi".equals(str(f, "lang")); }
}
