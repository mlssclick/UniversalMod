package universalmod.render.blur;

final class BlurGeometry {
    private BlurGeometry() {
    }

    static BlurPass normalize(BlurPass blur) {
        float maxRadius = Math.max(0.0f, Math.min(blur.width(), blur.height()) * 0.5f);
        float radiusTopLeft = clamp(blur.radiusTopLeft(), 0.0f, maxRadius);
        float radiusTopRight = clamp(blur.radiusTopRight(), 0.0f, maxRadius);
        float radiusBottomRight = clamp(blur.radiusBottomRight(), 0.0f, maxRadius);
        float radiusBottomLeft = clamp(blur.radiusBottomLeft(), 0.0f, maxRadius);
        float smoothness = Math.max(0.0f, blur.smoothness());
        float blurRadius = Math.max(0.0f, blur.blurRadius());
        float verticalSplit = blur.verticalSplit();
        if (!Float.isFinite(verticalSplit) || verticalSplit < 0.0f) {
            verticalSplit = BlurPass.NO_VERTICAL_SPLIT;
        } else {
            verticalSplit = clamp(verticalSplit, 0.0f, Math.max(blur.height(), 0.0f));
        }
        if (radiusTopLeft == blur.radiusTopLeft()
                && radiusTopRight == blur.radiusTopRight()
                && radiusBottomRight == blur.radiusBottomRight()
                && radiusBottomLeft == blur.radiusBottomLeft()
                && smoothness == blur.smoothness()
                && blurRadius == blur.blurRadius()
                && verticalSplit == blur.verticalSplit()) {
            return blur;
        }
        return new BlurPass(
                blur.x(), blur.y(), blur.width(), blur.height(),
                radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                smoothness, blurRadius,
                blur.colorTopLeft(), blur.colorTopRight(), blur.colorBottomRight(), blur.colorBottomLeft(),
                blur.algorithm(), verticalSplit, blur.splitColor()
        );
    }


    static int iterations(float blurRadius) {
        return BlurQualityPolicy.iterations(blurRadius);
    }

    static float renderScale(float blurRadius, int width, int height) {
        return BlurQualityPolicy.renderScale(blurRadius, width, height);
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
