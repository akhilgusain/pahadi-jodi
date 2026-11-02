package in.pahadijodi.matchmaking;

/** Reaches the package-private educationLevel() through reflection so the engine API stays small. */
final class CompatibilityEngineAccess {
    static int level(String edu) {
        try {
            var m = in.pahadijodi.matchmaking.scoring.CompatibilityEngine.class.getDeclaredMethod("educationLevel", String.class);
            m.setAccessible(true);
            return (int) m.invoke(null, edu);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
