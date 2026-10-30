package universalmod.module.render;

import net.minecraft.client.Minecraft;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.NumberSetting;
import universalmod.render.HitColorOverlayRegistry;

import java.awt.Color;

public final class HitColor extends Module {
    private static volatile HitColor instance;

    private final ColorSetting hitColor = register(new ColorSetting(
            "Hit Color",
            "Changes the color when entities take damage.",
            new Color(255, 0, 0, 255)
    ));
    private final BooleanSetting armor = register(new BooleanSetting(
            "Armor",
            "Apply hit color to armor too.",
            true
    ));
    private final NumberSetting alpha = register(new NumberSetting(
            "Alpha",
            "Transparency of the hit overlay color.",
            100.0,
            0.0,
            100.0,
            1.0
    ));

    public HitColor() {
        super("hitcolor", "Hit Color", ModuleCategory.RENDER);
        instance = this;
        hitColor.addListener((setting, oldValue, newValue) -> HitColorOverlayRegistry.reloadAll());
        armor.addListener((setting, oldValue, newValue) -> HitColorOverlayRegistry.reloadAll());
        alpha.addListener((setting, oldValue, newValue) -> HitColorOverlayRegistry.reloadAll());
    }

    public static HitColor getInstance() {
        return instance;
    }

    public int[] getHitColorRGBA() {
        Color color = hitColor.getValue();
        int alphaValue = (int) Math.round((alpha.getValue() / 100.0) * 255.0);
        return new int[] {
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                Math.max(0, Math.min(255, alphaValue))
        };
    }

    public boolean shouldTintArmor() {
        return Boolean.TRUE.equals(armor.getValue());
    }

    @Override
    protected void onEnable() {
        HitColorOverlayRegistry.reloadAll();
    }

    @Override
    protected void onDisable() {
        HitColorOverlayRegistry.reloadAll();
    }

    @Override
    public void onTick(Minecraft client) {
        HitColorOverlayRegistry.reloadAll();
    }
}
