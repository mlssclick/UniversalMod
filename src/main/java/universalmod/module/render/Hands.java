package universalmod.module.render;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.render.hand.HandsRenderer;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;

@ModuleInformation(moduleName = "Hands", moduleCategory = ModuleCategory.RENDER)
public final class Hands extends Module {
    private static Hands instance;

    public final ModeSetting mode = new ModeSetting("Mode", "Shader", "Shader", "Shader+Outline", "Outline", "Smoke");
    public final ModeSetting shaderMode = new ModeSetting("Shader", "Wave", "Wave", "Nebula");
    public final ColorSetting shaderColor = new ColorSetting("Color", "Shader Color", 0xFFB23AC4);
    public final NumberSetting waveSpeed = new NumberSetting("Wave Speed", 110.0, 10.0, 500.0, 1.0);
    public final NumberSetting waveScale = new NumberSetting("Wave Scale", 160.0, 100.0, 300.0, 1.0);
    public final NumberSetting fill = new NumberSetting("Fill", 59.0, 0.0, 100.0, 1.0);
    public final NumberSetting alpha = new NumberSetting("Alpha", 100.0, 0.0, 100.0, 1.0);
    public final ModeSetting outlineColorMode = new ModeSetting("Outline Color Mode", "Gradient", "Gradient", "Static");
    public final ColorSetting outlineColor = new ColorSetting("Outline Color", 0xFFFFFFFF);
    public final ColorSetting outlineSecondColor = new ColorSetting("Second Color", 0xFF7FF2FF);
    public final NumberSetting outlineRadius = new NumberSetting("Radius", 8.0, 2.0, 24.0, 1.0);
    public final NumberSetting glowStrength = new NumberSetting("Glow Strength", 1.8, 0.25, 5.0, 0.05);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", 1.5, 0.5, 6.0, 0.5);
    public final NumberSetting outlineOpacity = new NumberSetting("Outline Opacity", 90.0, 0.0, 100.0, 1.0);
    public final ColorSetting smokeColor = new ColorSetting("Color", 0xE6FFFFFF);
    public final NumberSetting smokeStrength = new NumberSetting("Strength", 0.85, 0.0, 2.0, 0.01);
    public final NumberSetting smokeRiseSpeed = new NumberSetting("Rise Speed", 0.0, 0.0, 2.0, 0.01);
    public final NumberSetting smokeWobble = new NumberSetting("Wobble", 0.65, 0.0, 2.0, 0.01);
    public final NumberSetting smokeLength = new NumberSetting("Length", 0.95, 0.1, 2.5, 0.01);
    public final NumberSetting smokeBrightness = new NumberSetting("Brightness", 0.9, 0.0, 2.0, 0.01);

    public Hands() {
        instance = this;
        shaderMode.setVisible(this::showsShaderSettings);
        shaderColor.setVisible(this::showsShaderSettings);
        waveSpeed.setVisible(this::showsShaderSettings);
        waveScale.setVisible(this::showsShaderSettings);
        fill.setVisible(this::showsShaderSettings);
        alpha.setVisible(this::showsShaderSettings);
        outlineColorMode.setVisible(this::showsOutlineSettings);
        outlineColor.setVisible(this::showsOutlineSettings);
        outlineSecondColor.setVisible(() -> showsOutlineSettings() && outlineColorMode.is("Gradient"));
        outlineRadius.setVisible(this::showsOutlineSettings);
        glowStrength.setVisible(this::showsOutlineSettings);
        outlineWidth.setVisible(this::showsOutlineSettings);
        outlineOpacity.setVisible(this::showsOutlineSettings);
        smokeColor.setVisible(this::showsSmokeSettings);
        smokeStrength.setVisible(this::showsSmokeSettings);
        smokeRiseSpeed.setVisible(this::showsSmokeSettings);
        smokeWobble.setVisible(this::showsSmokeSettings);
        smokeLength.setVisible(this::showsSmokeSettings);
        smokeBrightness.setVisible(this::showsSmokeSettings);
    }

    public static Hands getInstance() { return instance; }
    public static boolean isActive() {
        Hands module = instance;
        if (module == null || !module.isEnabled()) return false;
        if (module.shouldRenderSmoke()) return true;
        if (module.shouldRenderShader() && module.getFill() > 0.0F && module.getAlpha() > 0.0F) return true;
        return module.shouldRenderOutline() && module.getOutlineOpacity() > 0.0F;
    }
    public boolean shouldRenderShader() { return mode.is("Shader") || mode.is("Shader+Outline"); }
    public boolean shouldRenderOutline() { return mode.is("Outline") || mode.is("Shader+Outline"); }
    public boolean shouldRenderSmoke() { return mode.is("Smoke"); }
    public String getShaderMode() { return shaderMode.getValue(); }
    public int getShaderColor() { return shaderColor.getValue(); }
    public float getWaveSpeed() { return waveSpeed.getFloat(); }
    public float getWaveScale() { return waveScale.getFloat(); }
    public float getFill() { return fill.getFloat(); }
    public float getAlpha() { return alpha.getFloat(); }
    public int getOutlineTopColor() { return outlineColor.getValue(); }
    public int getOutlineBottomColor() { return outlineColorMode.is("Static") ? outlineColor.getValue() : outlineSecondColor.getValue(); }
    public boolean isOutlineStatic() { return outlineColorMode.is("Static"); }
    public float getOutlineRadius() { return outlineRadius.getFloat(); }
    public float getGlowStrength() { return glowStrength.getFloat(); }
    public float getOutlineWidth() { return outlineWidth.getFloat(); }
    public float getOutlineOpacity() { return outlineOpacity.getFloat() / 100.0F; }
    public float getSmokeStrength() { return smokeStrength.getFloat(); }
    public float getSmokeRiseSpeed() { return smokeRiseSpeed.getFloat(); }
    public float getSmokeWobble() { return smokeWobble.getFloat(); }
    public float getSmokeLength() { return smokeLength.getFloat(); }
    public float getSmokeBrightness() { return smokeBrightness.getFloat(); }
    public int getSmokeResolvedColor() { return smokeColor.getValue(); }
    private boolean showsShaderSettings() { return shouldRenderShader(); }
    private boolean showsOutlineSettings() { return shouldRenderOutline(); }
    private boolean showsSmokeSettings() { return shouldRenderSmoke(); }

    @Override
    public void onDisable() {
        HandsRenderer.reset();
    }
}
