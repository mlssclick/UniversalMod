package universalmod.module.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import universalmod.mixin.accessor.WorldRendererFullbrightAccessor;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;
import universalmod.util.render.world.GlowPropagator;

import java.util.HashSet;
import java.util.Set;

@ModuleInformation(moduleName = "Fullbright", moduleCategory = ModuleCategory.MISC)
public final class Fullbright extends Module {
    private static final long ANIMATION_DURATION_NANOS = 450_000_000L;
    private static Fullbright instance;
    public final ModeSetting mode = new ModeSetting("Mode", "Dynamic", "Gamma", "Dynamic");
    public final NumberSetting dynamicRadius = new NumberSetting("Dynamic Radius", 12.0, 4.0, 12.0, 0.5).setVisible(() -> mode.is("Dynamic"));
    public final NumberSetting dynamicLight = new NumberSetting("Dynamic Light", 15.0, 8.0, 15.0, 1.0).setVisible(() -> mode.is("Dynamic"));
    public final BooleanSetting onlyInCave = new BooleanSetting("Only In Cave", true).setVisible(() -> mode.is("Dynamic"));
    private boolean caveActive;
    private BlockPos lastLightPos;
    private float currentRadius = -1.0f;
    private int lastLight = -1;
    private boolean dynamicLightApplied;
    private float animationValue;
    private float animationStart;
    private float animationTarget;
    private long animationStartNanos;

    public Fullbright() {
        instance = this;
        mode.setDescription("Fullbright mode.");
        dynamicRadius.setDescription("Dynamic light propagation radius.");
        dynamicLight.setDescription("Dynamic light level.");
        onlyInCave.setDescription("Enable dynamic light only in dark covered areas.");
    }

    public static Fullbright getInstance() {
        return instance;
    }

    public boolean isGammaMode() {
        return isEnabled() && mode.is("Gamma");
    }

    public boolean isDynamicMode() {
        return isEnabled() && mode.is("Dynamic");
    }

    @Override
    public void onEnable() {
        caveActive = false;
        resetAnimation();
    }

    @Override
    public void onDisable() {
        clearDynamicLight();
        caveActive = false;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            caveActive = false;
            clearDynamicLight();
            return;
        }
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        if (mc.player == null || mc.world == null) {
            clearDynamicLight();
            return;
        }
        updateDynamicLight(shouldApplyDynamicLight());
    }

    private boolean shouldApplyDynamicLight() {
        if (!isDynamicMode()) return false;
        return !onlyInCave.getValue() || isInDarkness();
    }

    private boolean isInDarkness() {
        if (mc.player == null || mc.world == null) {
            caveActive = false;
            return false;
        }
        BlockPos pos = BlockPos.ofFloored(mc.player.getEyePos());
        int skyLight = mc.world.getLightLevel(LightType.SKY, pos);
        if (mc.world.isSkyVisible(pos) || skyLight >= 8) caveActive = false;
        else if (skyLight <= 4) caveActive = true;
        return caveActive;
    }

    private void updateDynamicLight(boolean shouldApply) {
        float factor = animateTo(shouldApply ? 1.0f : 0.0f);
        int light = MathHelper.clamp(Math.round(dynamicLight.getFloat() * factor), 0, 15);
        if (light <= 0) {
            removeDynamicLight();
            return;
        }
        BlockPos cameraPos = BlockPos.ofFloored(mc.gameRenderer.getCamera().getPos());
        float radius = dynamicRadius.getFloat();
        if (dynamicLightApplied && cameraPos.equals(lastLightPos) && Float.compare(radius, currentRadius) == 0 && light == lastLight) return;
        BlockPos oldPos = lastLightPos;
        int oldRadius = MathHelper.ceil(currentRadius);
        GlowPropagator.propagate(mc.world, cameraPos, radius, light);
        relightChunks(oldPos, oldRadius, cameraPos, MathHelper.ceil(radius));
        lastLightPos = cameraPos;
        currentRadius = radius;
        lastLight = light;
        dynamicLightApplied = true;
    }

    private float animateTo(float target) {
        long now = System.nanoTime();
        float current = animationCurrent(now);
        if (Float.compare(animationTarget, target) != 0) {
            animationStart = current;
            animationTarget = target;
            animationStartNanos = now;
        }
        animationValue = animationCurrent(now);
        return animationValue;
    }

    private float animationCurrent(long now) {
        if (animationStartNanos == 0L) return animationTarget;
        float t = Math.min(1.0f, Math.max(0.0f, (float)(now - animationStartNanos) / (float)ANIMATION_DURATION_NANOS));
        float eased = 0.5f * (1.0f - (float)Math.cos(Math.PI * t));
        return animationStart + (animationTarget - animationStart) * eased;
    }

    private void resetAnimation() {
        animationValue = 0.0f;
        animationStart = 0.0f;
        animationTarget = 0.0f;
        animationStartNanos = 0L;
    }

    private void clearDynamicLight() {
        resetAnimation();
        if (!dynamicLightApplied) {
            GlowPropagator.clear();
            return;
        }
        BlockPos oldPos = lastLightPos;
        int oldRadius = MathHelper.ceil(currentRadius);
        GlowPropagator.clear();
        relightChunks(oldPos, oldRadius, null, 0);
        resetDynamicState();
    }

    private void removeDynamicLight() {
        if (!dynamicLightApplied) return;
        BlockPos oldPos = lastLightPos;
        int oldRadius = MathHelper.ceil(currentRadius);
        GlowPropagator.clear();
        relightChunks(oldPos, oldRadius, null, 0);
        resetDynamicState();
    }

    private void resetDynamicState() {
        lastLightPos = null;
        currentRadius = -1.0f;
        lastLight = -1;
        dynamicLightApplied = false;
    }

    private void relightChunks(BlockPos oldPos, int oldRadius, BlockPos newPos, int newRadius) {
        if (mc.world == null || mc.worldRenderer == null) return;
        if (((WorldRendererFullbrightAccessor) (Object) mc.worldRenderer).universalmod$getChunks() == null) return;
        Set<BlockPos> sections = new HashSet<>();
        collectLitChunks(sections, oldPos, oldRadius);
        collectLitChunks(sections, newPos, newRadius);
        WorldRendererFullbrightAccessor renderer = (WorldRendererFullbrightAccessor) (Object) mc.worldRenderer;
        for (BlockPos section : sections) renderer.universalmod$scheduleChunkRender(section.getX(), section.getY(), section.getZ(), false);
    }

    private void collectLitChunks(Set<BlockPos> sections, BlockPos source, int radius) {
        if (source == null || radius <= 0) return;
        int inflated = radius + 1;
        int minX = Math.floorDiv(source.getX() - inflated, 16);
        int minY = Math.floorDiv(source.getY() - inflated, 16);
        int minZ = Math.floorDiv(source.getZ() - inflated, 16);
        int maxX = Math.floorDiv(source.getX() + inflated, 16);
        int maxY = Math.floorDiv(source.getY() + inflated, 16);
        int maxZ = Math.floorDiv(source.getZ() + inflated, 16);
        double maxDistanceSq = (double)inflated * inflated;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int closestX = MathHelper.clamp(source.getX(), x << 4, (x << 4) + 15);
                    int closestY = MathHelper.clamp(source.getY(), y << 4, (y << 4) + 15);
                    int closestZ = MathHelper.clamp(source.getZ(), z << 4, (z << 4) + 15);
                    if (source.getSquaredDistance(closestX, closestY, closestZ) <= maxDistanceSq) sections.add(new BlockPos(x, y, z));
                }
            }
        }
    }
}
