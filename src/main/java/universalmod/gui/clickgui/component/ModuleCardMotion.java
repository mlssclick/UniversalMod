package universalmod.gui.clickgui.component;

final class ModuleCardMotion {
    private static final long TITLE_HOLD_MS = 750L;

    private ModuleCardMotion() {
    }

    static float titleOffset(float textWidth, float clipWidth, long now) {
        float overflow = Math.max(0.0f, textWidth - clipWidth);
        if (overflow <= 0.25f) return 0.0f;
        long travel = Math.max(1500L, 1250L + Math.round(overflow * 32.0f));
        long cycle = TITLE_HOLD_MS + travel + TITLE_HOLD_MS + travel;
        long phase = Math.floorMod(now, cycle);
        if (phase < TITLE_HOLD_MS) return 0.0f;
        if (phase < TITLE_HOLD_MS + travel) return -overflow * smoothStep((phase - TITLE_HOLD_MS) / (float) travel);
        if (phase < TITLE_HOLD_MS + travel + TITLE_HOLD_MS) return -overflow;
        return -overflow * (1.0f - smoothStep((phase - TITLE_HOLD_MS - travel - TITLE_HOLD_MS) / (float) travel));
    }

    static float smoothStep(float value) {
        float v = clamp(value);
        return v * v * (3.0f - 2.0f * v);
    }

    static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    static boolean intersects(float x, float y, float width, float height, float clipX, float clipY, float clipWidth, float clipHeight) {
        return x < clipX + clipWidth && x + width > clipX && y < clipY + clipHeight && y + height > clipY;
    }
}
