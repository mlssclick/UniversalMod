package universalmod.module.render;

import java.awt.Color;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.SliderSetting;
import universalmod.render.HitColorOverlayRegistry;

@ModuleInformation(
        moduleName = "Hit Color",
        moduleCategory = ModuleCategory.RENDER
)
public final class HitColor extends Module {
    private static HitColor instance;

    public final ColorSetting hitColor = new ColorSetting("Hit Color", new Color(255, 0, 0, 255).getRGB());
    public final BooleanSetting armor = new BooleanSetting("Armor", true);
    public final SliderSetting alpha = new SliderSetting("Alpha", 100.0, 0.0, 100.0, 1.0);

    public HitColor() {
        instance = this;
    }

    public static HitColor getInstance() {
        return instance;
    }

    public int[] getHitColorRGBA() {
        Color color = new Color(hitColor.getValue(), true);
        int alphaValue = (int) Math.round((alpha.getValue() / 100.0) * 255.0);
        return new int[]{
                color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alphaValue))
        };
    }

    public boolean shouldTintArmor() {
        return armor.getValue();
    }

    @Override
    public void onEnable() {
        HitColorOverlayRegistry.reloadAll();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        HitColorOverlayRegistry.reloadAll();
        super.onDisable();
    }

    @Override
    public void onTick(net.minecraft.client.MinecraftClient tickClient) {
        HitColorOverlayRegistry.reloadAll();
    }
}
