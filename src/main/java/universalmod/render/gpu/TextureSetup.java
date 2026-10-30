package universalmod.render.gpu;

import java.util.function.IntSupplier;

public record TextureSetup(IntSupplier texture, boolean nearest) {
    private static final TextureSetup NONE = new TextureSetup(() -> 0, false);
    public static TextureSetup noTexture() { return NONE; }
    public static TextureSetup single(IntSupplier texture, boolean nearest) {
        return new TextureSetup(texture, nearest);
    }
}
