package universalmod.render.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import universalmod.render.color.ColorUtil;

public record BuiltEffectIcon(RegistryEntry<StatusEffect> effect, float x, float y, float size, int color) {
    public static final int DEFAULT_COLOR = ColorUtil.WHITE;

    public BuiltEffectIcon(RegistryEntry<StatusEffect> e, float x, float y, float size) {
        this(e, x, y, size, DEFAULT_COLOR);
    }

    public BuiltEffectIcon(StatusEffectInstance i, float x, float y, float size) {
        this(i == null ? null : i.getEffectType(), x, y, size, DEFAULT_COLOR);
    }

    public BuiltEffectIcon(StatusEffectInstance i, float x, float y, float size, int c) {
        this(i == null ? null : i.getEffectType(), x, y, size, c);
    }

    public boolean visible() {
        return effect != null && size > 0 && ((color >>> 24) & 255) > 0;
    }
}
