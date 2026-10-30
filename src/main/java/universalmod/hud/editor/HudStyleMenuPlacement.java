package universalmod.hud.editor;

import universalmod.hud.ElementScreen;


final class HudStyleMenuPlacement {
    private static final float SCREEN_MARGIN = 4.0F;
    private static final float ELEMENT_GAP = 8.0F;

    private HudStyleMenuPlacement() {
    }

    static Result resolve(
            HudPanel anchor,
            float width,
            float height,
            Side requestedSide,
            Side currentSide,
            boolean resolveSide
    ) {
        float right = anchor.x() + anchor.width() + u(ELEMENT_GAP);
        float left = anchor.x() - width - u(ELEMENT_GAP);
        ElementScreen screen = ElementScreen.current();
        Side chosen = resolveSide ? requestedSide : currentSide;
        float candidate = chosen == Side.RIGHT ? right : left;
        float margin = u(SCREEN_MARGIN);

        if (resolveSide && (candidate < margin || candidate + width > screen.width() - margin)) {
            Side opposite = chosen == Side.RIGHT ? Side.LEFT : Side.RIGHT;
            float oppositeX = opposite == Side.RIGHT ? right : left;
            if (oppositeX >= margin && oppositeX + width <= screen.width() - margin) {
                chosen = opposite;
                candidate = oppositeX;
            }
        }

        float x = clamp(candidate, margin, Math.max(margin, screen.width() - width - margin));
        float anchorOffset = Math.min(u(4.0F), Math.max(0.0F, anchor.height() * 0.12F));
        float y = clamp(anchor.y() + anchorOffset, margin,
                Math.max(margin, screen.height() - height - margin));
        return new Result(x, y, chosen);
    }

    private static float u(float value) {
        return HudEditorControls.u(value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    enum Side {
        LEFT,
        RIGHT
    }

    record Result(float x, float y, Side side) {
    }
}
