package universalmod.module.render;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.render.fireglow.FireGlowConfig;
import universalmod.render.fireglow.FireGlowRenderer;
import universalmod.render.fireglow.FireGlowState;

@ModuleInformation(
        moduleName = "Fire Glow",
        moduleCategory = ModuleCategory.RENDER
)
public final class FireGlow extends Module {
    private static FireGlow instance;
    private final BooleanSetting customColor = new BooleanSetting("Custom Color", false);
    private final ColorSetting color = new ColorSetting("Color", 0xFFFFFFFF).setVisible(customColor::getValue);

    public FireGlow() {
        instance = this;
        FireGlowConfig.load();
        customColor.setValue(FireGlowConfig.hasCustomColor());
        color.setValue(0xFF000000 | (FireGlowConfig.getRed() << 16) | (FireGlowConfig.getGreen() << 8) | FireGlowConfig.getBlue());
    }

    public static FireGlow getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static boolean hasCustomColorEnabled() {
        return isActive() && instance.customColor.getValue();
    }

    public static int getRedValue() {
        return instance == null ? 255 : (instance.color.getValue() >> 16) & 255;
    }

    public static int getGreenValue() {
        return instance == null ? 255 : (instance.color.getValue() >> 8) & 255;
    }

    public static int getBlueValue() {
        return instance == null ? 255 : instance.color.getValue() & 255;
    }

    public FireGlowState renderState() {
        int value = color.getValue();
        return new FireGlowState(
                isEnabled(), customColor.getValue(),
                (value >> 16) & 255, (value >> 8) & 255, value & 255
        );
    }

    public void syncToConfig() {
        FireGlowConfig.setCustomColor(customColor.getValue());
        FireGlowConfig.setRed(getRedValue());
        FireGlowConfig.setGreen(getGreenValue());
        FireGlowConfig.setBlue(getBlueValue());
    }

    @Override
    public void onDisable() {
        syncToConfig();
        FireGlowConfig.save();
        FireGlowRenderer.reset();
        super.onDisable();
    }

    public void shutdown() {
        syncToConfig();
        FireGlowConfig.save();
        FireGlowRenderer.reset();
    }
}
