package universalmod.hud.editor;

import universalmod.hud.ElementScreen;
import universalmod.module.render.Scoreboard;


final class ScoreboardStyleMenuPlacement {
    private ScoreboardStyleMenuPlacement() {
    }

    static MenuPlacement resolveMenu(
            Scoreboard scoreboard,
            float popupWidth,
            float popupHeight,
            float gap,
            float margin,
            Side side,
            boolean resolveSide
    ) {
        ElementScreen screen = ElementScreen.current();
        float right = scoreboard.editorX() + scoreboard.editorWidth() + gap;
        float left = scoreboard.editorX() - popupWidth - gap;
        Side chosen = side;
        float candidate = chosen == Side.RIGHT ? right : left;
        if (resolveSide && (candidate < margin || candidate + popupWidth > screen.width() - margin)) {
            chosen = chosen == Side.RIGHT ? Side.LEFT : Side.RIGHT;
            candidate = chosen == Side.RIGHT ? right : left;
        }
        float x = clamp(candidate, margin, screen.width() - popupWidth - margin);
        float y = clamp(
                scoreboard.editorY() + Math.min(4.0F, scoreboard.editorHeight() * 0.12F),
                margin,
                screen.height() - popupHeight - margin
        );
        return new MenuPlacement(x, y, chosen);
    }

    static PickerPlacement resolvePicker(
            float menuX,
            float rowY,
            float menuWidth,
            float pickerWidth,
            float pickerHeight,
            float pickerGap,
            float margin
    ) {
        ElementScreen screen = ElementScreen.current();
        float right = menuX + menuWidth + pickerGap;
        float left = menuX - pickerWidth - pickerGap;
        float maxX = screen.width() - pickerWidth - margin;
        float x;
        if (right <= maxX) {
            x = right;
        } else if (left >= margin) {
            x = left;
        } else {
            x = clamp(right, margin, Math.max(margin, maxX));
        }
        float y = clamp(rowY - 4.0F, margin, Math.max(margin, screen.height() - pickerHeight - margin));
        return new PickerPlacement(x, y);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    enum Side {
        LEFT,
        RIGHT
    }

    record MenuPlacement(float x, float y, Side side) {
    }

    record PickerPlacement(float x, float y) {
    }
}
