package universalmod.render.font.msdf;

import java.util.function.Supplier;

 


public final class Fonts {
    private static volatile long generation;

    private static final class Reloadable implements Supplier<MsdfFont> {
        private final String path;
        private volatile long loadedGeneration = -1;
        private volatile MsdfFont current;

        private Reloadable(String path) { this.path = path; }

        @Override
        public MsdfFont get() {
            long expected = generation;
            MsdfFont cached = current;
            if (cached != null && loadedGeneration == expected) return cached;
            synchronized (this) {
                expected = generation;
                if (current == null || loadedGeneration != expected) {
                    
                    MsdfFont replacement = MsdfFont.load(path);
                    current = replacement;
                    loadedGeneration = expected;
                }
                return current;
            }
        }
    }

    public static final Supplier<MsdfFont> BOLD = new Reloadable("sf_medium");
    public static final Supplier<MsdfFont> SEMIBOLD = BOLD;
    public static final Supplier<MsdfFont> REGULAR = new Reloadable("sf_regular");
     
    public static final Supplier<MsdfFont> WORLD_LYRICS = new Reloadable("world_lyrics/sf_regular");
    public static final Supplier<MsdfFont> ONEST = new Reloadable("onest_regular");
    public static final Supplier<MsdfFont> GT = new Reloadable("gt_regular");
    public static final Supplier<MsdfFont> GUI_ICONS = new Reloadable("guiicons");
    public static final Supplier<MsdfFont> ICONNEW = new Reloadable("iconnew");
    public static final Supplier<MsdfFont> DELTA_ICONS = new Reloadable("delta_icons");
    public static final Supplier<MsdfFont> ICONS = new Reloadable("icons");
    public static final Supplier<MsdfFont> DIVINE = new Reloadable("divine");
    public static final Supplier<MsdfFont> VIREX_ICONS = new Reloadable("virex_icons");
    public static final Supplier<MsdfFont> VIREX_WONDERFUL = new Reloadable("virex_wonderful");

    static void invalidate() { generation++; }
    private Fonts() {}
}
