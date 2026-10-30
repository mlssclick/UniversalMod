package universalmod.hud.interaction;

import universalmod.hud.ElementScreen;

public final class DragSession {
    private static final float MAX_TILT_DEGREES = 25.0F;
    private static final float TILT_FROM_MOUSE_DELTA = 4.0F;
    private static final float DRAG_TILT_LERP = 0.14F;
    private static final float RELEASE_TILT_LERP = 0.10F;
    private static final float TILT_DELTA_SMOOTHING = 0.18F;
    private static final float TILT_TARGET_SMOOTHING = 0.22F;
    private static final float TILT_DEADZONE = 0.18F;
    private static final float DRAG_SCALE_MULTIPLIER = 1.01F;
    private static final float DRAG_SCALE_LERP = 0.10F;
    private static final float RELEASE_SCALE_LERP = 0.02F;

    private boolean moving;
    private float pointerOffsetX;
    private float pointerOffsetY;
    private float dragTiltDegrees;
    private float targetTiltDegrees;
    private float smoothedMouseDeltaX;
    private float previousMouseX;
    private boolean hasPreviousMouseX;
    private float dragScale = 1.0F;
    private float lineAlpha;
    private boolean snapVertical;
    private boolean snapHorizontal;

    public void begin(float mouseX, float mouseY, float visualX, float visualY) {
        moving = true;
        pointerOffsetX = mouseX - visualX;
        pointerOffsetY = mouseY - visualY;
        previousMouseX = mouseX;
        hasPreviousMouseX = true;
        smoothedMouseDeltaX = 0.0F;
    }

    public DragTarget update(float mouseX, float mouseY, float visualOffsetX, float visualOffsetY,
                             float width, float height, ElementScreen screen) {
        float targetX = mouseX - pointerOffsetX - visualOffsetX;
        float targetY = mouseY - pointerOffsetY - visualOffsetY;
        SnapEngine.SnapResult snap = SnapEngine.snap(targetX, targetY, width, height, screen,
                SnapEngine.DEFAULT_DISTANCE);
        snapVertical = snap.verticalGuide();
        snapHorizontal = snap.horizontalGuide();

        float mouseDeltaX = hasPreviousMouseX ? mouseX - previousMouseX : 0.0F;
        previousMouseX = mouseX;
        hasPreviousMouseX = true;
        smoothedMouseDeltaX = lerp(smoothedMouseDeltaX, mouseDeltaX, TILT_DELTA_SMOOTHING);
        float desiredTilt = Math.abs(smoothedMouseDeltaX) <= TILT_DEADZONE
                ? 0.0F
                : clamp(smoothedMouseDeltaX * TILT_FROM_MOUSE_DELTA, -MAX_TILT_DEGREES, MAX_TILT_DEGREES);
        targetTiltDegrees = lerp(targetTiltDegrees, desiredTilt, TILT_TARGET_SMOOTHING);
        dragTiltDegrees = lerp(dragTiltDegrees, targetTiltDegrees, DRAG_TILT_LERP);
        dragScale = lerp(dragScale, DRAG_SCALE_MULTIPLIER, DRAG_SCALE_LERP);
        return new DragTarget(snap.x(), snap.y());
    }

    public void end() {
        moving = false;
        hasPreviousMouseX = false;
        targetTiltDegrees = 0.0F;
    }

    public void cancel() {
        moving = false;
        hasPreviousMouseX = false;
        targetTiltDegrees = 0.0F;
        snapVertical = false;
        snapHorizontal = false;
    }

    public void tickAnimation() {
        if (!moving) {
            smoothedMouseDeltaX = lerp(smoothedMouseDeltaX, 0.0F, TILT_DELTA_SMOOTHING);
            targetTiltDegrees = lerp(targetTiltDegrees, 0.0F, TILT_TARGET_SMOOTHING);
            dragTiltDegrees = lerp(dragTiltDegrees, targetTiltDegrees, RELEASE_TILT_LERP);
            dragScale = lerp(dragScale, 1.0F, RELEASE_SCALE_LERP);
            if (Math.abs(dragTiltDegrees) < 0.01F) dragTiltDegrees = 0.0F;
            if (Math.abs(dragScale - 1.0F) < 0.0005F) dragScale = 1.0F;
        }
        float targetAlpha = moving && (snapVertical || snapHorizontal) ? 1.0F : 0.0F;
        lineAlpha = lerp(lineAlpha, targetAlpha, moving ? 0.20F : 0.10F);
        if (lineAlpha < 0.01F) lineAlpha = 0.0F;
        if (!moving && lineAlpha == 0.0F) {
            snapVertical = false;
            snapHorizontal = false;
        }
    }

    public boolean moving() { return moving; }
    public float tiltDegrees() { return dragTiltDegrees; }
    public float scale() { return dragScale; }
    public float lineAlpha() { return lineAlpha; }
    public boolean snapVertical() { return snapVertical; }
    public boolean snapHorizontal() { return snapHorizontal; }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float lerp(float from, float to, float speed) {
        return from + (to - from) * speed;
    }

    public record DragTarget(float x, float y) {
    }
}
