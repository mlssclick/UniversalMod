package universalmod.render.blur;

import com.mojang.blaze3d.pipeline.TextureTarget;

final class BlurTargetPool implements AutoCloseable {
    private final TextureTarget[] downTargets;
    private final TextureTarget[] upTargets;
    private TextureTarget resultTarget;

    BlurTargetPool(int maxIterations) {
        int capacity = Math.max(1, maxIterations);
        downTargets = new TextureTarget[capacity];
        upTargets = new TextureTarget[capacity];
    }

    void ensure(int width, int height, float renderScale, int iterations) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int count = Math.max(0, Math.min(iterations, downTargets.length));
        int targetWidth = Math.max(Math.round(width * renderScale), 1);
        int targetHeight = Math.max(Math.round(height * renderScale), 1);
        resultTarget = ensureTarget(resultTarget, "UNIVERSALMOD_blur_result", targetWidth, targetHeight);

        for (int i = 0; i < count; i++) {
            downTargets[i] = ensureTarget(downTargets[i], "UNIVERSALMOD_blur_down_" + i, targetWidth, targetHeight);
            if (i < count - 1) {
                upTargets[i] = ensureTarget(upTargets[i], "UNIVERSALMOD_blur_up_" + i, targetWidth, targetHeight);
            }
            targetWidth = Math.max(targetWidth / 2, 1);
            targetHeight = Math.max(targetHeight / 2, 1);
        }
    }

    TextureTarget result() {
        return resultTarget;
    }

    TextureTarget down(int index) {
        return downTargets[index];
    }

    TextureTarget up(int index) {
        return upTargets[index];
    }

    private static TextureTarget ensureTarget(TextureTarget target, String name, int width, int height) {
        if (target == null) {
            return new TextureTarget(name, width, height, false);
        }
        if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        return target;
    }

    @Override
    public void close() {
        destroy(resultTarget);
        resultTarget = null;
        for (int i = 0; i < downTargets.length; i++) {
            destroy(downTargets[i]);
            downTargets[i] = null;
            destroy(upTargets[i]);
            upTargets[i] = null;
        }
    }

    private static void destroy(TextureTarget target) {
        if (target != null) {
            target.destroyBuffers();
        }
    }
}
