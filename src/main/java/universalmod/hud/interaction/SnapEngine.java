package universalmod.hud.interaction;

import universalmod.hud.ElementScreen;


public final class SnapEngine {
    public static final float DEFAULT_DISTANCE = 10.0F;

    private SnapEngine() {
    }

    public static SnapResult snap(float targetX, float targetY, float width, float height,
                                  ElementScreen screen, float distance) {
        if (screen == null || !screen.valid()) {
            return new SnapResult(targetX, targetY, false, false);
        }
        float safeDistance = Math.max(0.0F, distance);
        float centerX = targetX + width * 0.5F;
        float centerY = targetY + height * 0.5F;
        float snappedX = targetX;
        float snappedY = targetY;
        boolean vertical = false;
        boolean horizontal = false;

        for (float line : horizontalGuidePositions(screen.width())) {
            if (Math.abs(centerX - line) <= safeDistance) {
                snappedX = line - width * 0.5F;
                vertical = true;
                break;
            }
        }
        for (float line : verticalGuidePositions(screen.height())) {
            if (Math.abs(centerY - line) <= safeDistance) {
                snappedY = line - height * 0.5F;
                horizontal = true;
                break;
            }
        }
        return new SnapResult(snappedX, snappedY, vertical, horizontal);
    }

    public static float[] horizontalGuidePositions(float screenWidth) {
        return new float[]{
                screenWidth * 0.5F,
                screenWidth * 0.25F,
                screenWidth * 0.125F,
                screenWidth / 1.15F,
                screenWidth / 1.35F
        };
    }

    public static float[] verticalGuidePositions(float screenHeight) {
        return new float[]{
                screenHeight * 0.5F,
                screenHeight * 0.25F,
                screenHeight * 0.125F,
                screenHeight / 1.15F,
                screenHeight / 1.35F
        };
    }

    public record SnapResult(float x, float y, boolean verticalGuide, boolean horizontalGuide) {
    }
}
