package universalmod.render;

public final class HitColorArmorRenderContext {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private HitColorArmorRenderContext() {
    }

    public static void setActive(boolean active) {
        ACTIVE.set(active);
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    public static void clear() {
        ACTIVE.remove();
    }
}
