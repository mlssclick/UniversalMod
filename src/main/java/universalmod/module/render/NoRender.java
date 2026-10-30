package universalmod.module.render;

import net.minecraft.block.AbstractPlantPartBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.client.render.Camera;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ModeListSetting;

@ModuleInformation(
        moduleName = "No Render",
        moduleCategory = ModuleCategory.RENDER
)
public final class NoRender extends Module {
    private static NoRender instance;

    public final ModeListSetting elements = new ModeListSetting(
            "Elements",
            new BooleanSetting("Hurt Cam", true),
            new BooleanSetting("Fire", true),
            new BooleanSetting("Particles", true),
            new BooleanSetting("Block Particles", true),
            new BooleanSetting("Hit Particles", true),
            new BooleanSetting("Block Overlay", true),
            new BooleanSetting("Plants", false),
            new BooleanSetting("Weather", true),
            new BooleanSetting("Clouds", true),
            new BooleanSetting("Glow", true),
            new BooleanSetting("Fog", false),
            new BooleanSetting("Damage", true),
            new BooleanSetting("Scoreboard", false),
            new BooleanSetting("Bossbar", false),
            new BooleanSetting("Vanilla Effects", false),
            new BooleanSetting("Wither Hearts", true),
            new BooleanSetting("Totem Animation", true),
            new BooleanSetting("Player Walk", false),
            new BooleanSetting("Player Swing", false)
    );

    private boolean plantsReloadState;

    public NoRender() {
        instance = this;
    }

    public static NoRender getInstance() {
        return instance;
    }

    public static boolean isActive(String element) {
        return instance != null && instance.isEnabled() && instance.elements.isEnabled(element);
    }

    public static boolean isNoFogEnabled() {
        return isActive("Fog");
    }

    public static boolean shouldRemoveVanillaFog(Camera camera) {
        if (!isNoFogEnabled()) {
            return false;
        }
        Atmosphere atmosphere = Atmosphere.getInstance();
        if (atmosphere != null && atmosphere.shouldApplyWinterFog(camera)) return false;
        Ambience ambience = Ambience.getInstance();
        return ambience == null || !ambience.shouldApplyCustomFog(camera);
    }

    @Override
    public void onEnable() {
        plantsReloadState = shouldHidePlants();
        if (plantsReloadState) {
            reloadWorldRenderer();
        }
        super.onEnable();
    }

    @Override
    public void onTick(net.minecraft.client.MinecraftClient tickClient) {
        boolean hidePlants = shouldHidePlants();
        if (hidePlants != plantsReloadState) {
            plantsReloadState = hidePlants;
            reloadWorldRenderer();
        }
    }

    public boolean shouldHidePlants() {
        return isEnabled() && elements.isEnabled("Plants");
    }

    public boolean shouldHideHitParticles() {
        return isEnabled() && elements.isEnabled("Hit Particles");
    }

    public boolean shouldHideParticles() {
        return isEnabled() && elements.isEnabled("Particles");
    }

    public boolean shouldHideBlockParticles() {
        return isEnabled() && elements.isEnabled("Block Particles");
    }

    public boolean shouldHideGlow() {
        return isEnabled() && elements.isEnabled("Glow");
    }

    public boolean shouldHideWeather() {
        return isEnabled() && elements.isEnabled("Weather");
    }

    public boolean shouldHideClouds() {
        return isEnabled() && elements.isEnabled("Clouds");
    }

    public boolean isPlantState(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof PlantBlock
                || block instanceof AbstractPlantPartBlock
                || block instanceof FlowerPotBlock;
    }

    private void reloadWorldRenderer() {
        if (mc.world != null && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    @Override
    public void onDisable() {
        boolean hadPlantsHidden = plantsReloadState;
        plantsReloadState = false;
        if (hadPlantsHidden) {
            reloadWorldRenderer();
        }
        super.onDisable();
    }
}
