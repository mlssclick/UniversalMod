package universalmod.render.effect;

import universalmod.render.color.ColorUtil;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public record EffectIcon(
        Holder<MobEffect> effect,
        float x,
        float y,
        float size,
        int color
) {
    public static final int DEFAULT_COLOR = ColorUtil.WHITE;

    public EffectIcon(Holder<MobEffect> effect, float x, float y, float size) {
        this(effect, x, y, size, DEFAULT_COLOR);
    }

    public EffectIcon(MobEffectInstance instance, float x, float y, float size) {
        this(instance == null ? null : instance.getEffect(), x, y, size, DEFAULT_COLOR);
    }

    public EffectIcon(MobEffectInstance instance, float x, float y, float size, int color) {
        this(instance == null ? null : instance.getEffect(), x, y, size, color);
    }

    public boolean visible() {
        return effect != null
                && effect.isBound()
                && size > 0.0f
                && effectiveAlpha(color) > 0;
    }

    private static int effectiveAlpha(int color) {
        int alpha = (color >>> 24) & 0xFF;
        return alpha == 0 && (color & 0x00FFFFFF) != 0 ? 255 : alpha;
    }
}
