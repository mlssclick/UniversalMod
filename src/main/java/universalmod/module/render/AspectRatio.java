package universalmod.module.render;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.SliderSetting;

@ModuleInformation(
        moduleName = "Aspect Ratio",
        moduleCategory = ModuleCategory.RENDER
)
public final class AspectRatio extends Module {
    private static final float ANIMATION_SECONDS = 0.24F;
    private static final float ANIMATION_EPSILON = 0.0005F;
    private static AspectRatio instance;

    public final SliderSetting ratio = new SliderSetting("Ratio", 1.5, 1.0, 2.0, 0.01);

    private boolean animationInitialized;
    private float animationStart;
    private float animationTarget;
    private float animationValue;
    private long animationStartNanos;
    private boolean policySuppressed;

    public AspectRatio() {
        instance = this;
    }

    public static AspectRatio getInstance() {
        return instance;
    }

    public static float resolveAnimatedRatio(float vanillaRatio) {
        AspectRatio module = instance;
        return module == null ? vanillaRatio : module.getAnimatedRatio(vanillaRatio);
    }

    public static float resolveAnimatedWidth(float currentWidth, float currentHeight) {
        if (currentHeight <= 0.0F) {
            return currentWidth;
        }
        float vanillaRatio = currentWidth / currentHeight;
        AspectRatio module = instance;
        if (module == null) return currentWidth;
        if (module.policySuppressed) {
            module.snapTo(vanillaRatio);
            return currentWidth;
        }
        float animatedRatio = module.getAnimatedRatio(vanillaRatio);
        if (!module.isEnabled() && Math.abs(animatedRatio - vanillaRatio) <= ANIMATION_EPSILON) return currentWidth;
        return currentHeight * animatedRatio;
    }

    public static float resolveRatio(float currentWidth, float currentHeight) {
        return resolveAnimatedWidth(currentWidth, currentHeight);
    }

    @Override public void onEnable() { policySuppressed = false; }
    @Override public void onDisable() { policySuppressed = isDesiredEnabled(); }

    private synchronized void snapTo(float value) {
        animationInitialized = true;
        animationStart = animationTarget = animationValue = value;
        animationStartNanos = System.nanoTime();
    }

    private synchronized float getAnimatedRatio(float vanillaRatio) {
        long now = System.nanoTime();
        if (!animationInitialized) {
            animationInitialized = true;
            animationStart = vanillaRatio;
            animationTarget = vanillaRatio;
            animationValue = vanillaRatio;
            animationStartNanos = now;
        }

        updateAnimation(now);
        float target = isEnabled() ? ratio.getFloatValue() : vanillaRatio;
        if (Math.abs(target - animationTarget) > ANIMATION_EPSILON) {
            animationStart = animationValue;
            animationTarget = target;
            animationStartNanos = now;
        }
        updateAnimation(now);
        return animationValue;
    }

    private void updateAnimation(long now) {
        float distance = Math.abs(animationTarget - animationStart);
        if (distance <= ANIMATION_EPSILON) {
            animationValue = animationTarget;
            return;
        }

        float elapsedSeconds = (now - animationStartNanos) / 1_000_000_000.0F;
        float progress = Math.max(0.0F, Math.min(1.0F, elapsedSeconds / ANIMATION_SECONDS));
        float eased = progress * progress * (3.0F - 2.0F * progress);
        animationValue = animationStart + (animationTarget - animationStart) * eased;
        if (progress >= 1.0F) {
            animationStart = animationTarget;
            animationValue = animationTarget;
        }
    }
}
