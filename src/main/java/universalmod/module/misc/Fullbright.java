package universalmod.module.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;
import universalmod.util.GlowPropagator;

import java.util.HashSet;
import java.util.Set;

public final class Fullbright extends Module {
    private static final long ANIMATION_DURATION_NANOS = 450_000_000L;
    private static Fullbright instance;
    public final ModeSetting mode;
    public final NumberSetting dynamicRadius;
    public final NumberSetting dynamicLight;
    public final BooleanSetting onlyInCave;
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
        super("full_bright", "Fullbright", ModuleCategory.MISC);
        instance = this;
        mode = register(new ModeSetting("Mode", "Fullbright mode.", "Dynamic", "Gamma", "Dynamic"));
        dynamicRadius = register(new NumberSetting("Dynamic Radius", "Dynamic light propagation radius.", 12.0, 4.0, 12.0, 0.5));
        dynamicLight = register(new NumberSetting("Dynamic Light", "Dynamic light level.", 15.0, 8.0, 15.0, 1.0));
        onlyInCave = register(new BooleanSetting("Only In Cave", "Enable dynamic light only in dark covered areas.", true));
        dynamicRadius.visibleWhen(() -> mode.is("Dynamic"));
        dynamicLight.visibleWhen(() -> mode.is("Dynamic"));
        onlyInCave.visibleWhen(() -> mode.is("Dynamic"));
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
    protected void onEnable() {
        caveActive = false;
        resetAnimation();
    }

    @Override
    protected void onDisable() {
        clearDynamicLight();
        caveActive = false;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            caveActive = false;
            clearDynamicLight();
            return;
        }
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        if (mc.player == null || mc.level == null) {
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
        if (mc.player == null || mc.level == null) {
            caveActive = false;
            return false;
        }
        BlockPos pos = BlockPos.containing(mc.player.getEyePosition());
        int skyLight = mc.level.getBrightness(LightLayer.SKY, pos);
        if (mc.level.canSeeSky(pos) || skyLight >= 8) caveActive = false;
        else if (skyLight <= 4) caveActive = true;
        return caveActive;
    }

    private void updateDynamicLight(boolean shouldApply) {
        float factor = animateTo(shouldApply ? 1.0f : 0.0f);
        int light = Mth.clamp(Math.round(dynamicLight.getFloat() * factor), 0, 15);
        if (light <= 0) {
            removeDynamicLight();
            return;
        }
        BlockPos cameraPos = BlockPos.containing(mc.gameRenderer.getMainCamera().position());
        float radius = dynamicRadius.getFloat();
        if (dynamicLightApplied && cameraPos.equals(lastLightPos) && Float.compare(radius, currentRadius) == 0 && light == lastLight) return;
        BlockPos oldPos = lastLightPos;
        int oldRadius = Mth.ceil(currentRadius);
        GlowPropagator.propagate(mc.level, cameraPos, radius, light);
        relightChunks(oldPos, oldRadius, cameraPos, Mth.ceil(radius));
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
        int oldRadius = Mth.ceil(currentRadius);
        GlowPropagator.clear();
        relightChunks(oldPos, oldRadius, null, 0);
        resetDynamicState();
    }

    private void removeDynamicLight() {
        if (!dynamicLightApplied) return;
        BlockPos oldPos = lastLightPos;
        int oldRadius = Mth.ceil(currentRadius);
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
        if (mc.levelRenderer == null || mc.level == null || mc.player == null) return;
        Set<BlockPos> sections = new HashSet<>();
        collectLitChunks(sections, oldPos, oldRadius);
        collectLitChunks(sections, newPos, newRadius);
        for (BlockPos section : sections) {
            if (mc.levelRenderer == null || mc.level == null || mc.player == null) return;
            try {
                mc.levelRenderer.setSectionDirty(section.getX(), section.getY(), section.getZ());
            } catch (NullPointerException rendererUnavailable) {
                return;
            }
        }
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
                    int closestX = Mth.clamp(source.getX(), x << 4, (x << 4) + 15);
                    int closestY = Mth.clamp(source.getY(), y << 4, (y << 4) + 15);
                    int closestZ = Mth.clamp(source.getZ(), z << 4, (z << 4) + 15);
                    double dx = source.getX() - closestX;
                    double dy = source.getY() - closestY;
                    double dz = source.getZ() - closestZ;
                    if (dx * dx + dy * dy + dz * dz <= maxDistanceSq) sections.add(new BlockPos(x, y, z));
                }
            }
        }
    }
}
