package universalmod.gui.ui.control;

import net.minecraft.client.gui.DrawContext;
import universalmod.render.Render2D;
import universalmod.render.font.FontType;
public final class MarqueeTextControl {
    private float phase;
    private boolean active;
    private int returnDirection;
    private long lastNanos;

    public void reset() {
        phase = 0.0F;
        active = false;
        returnDirection = 0;
        lastNanos = System.nanoTime();
    }

    public void render(DrawContext graphics, String text, float x, float y, float size,
                       float availableWidth, boolean hovered, int color, float scale) {
        if (text == null || text.isEmpty() || availableWidth <= 0.0F) {
            return;
        }

        float textWidth = Render2D.textWidth(FontType.SEMIBOLD, text, size);
        if (textWidth <= availableWidth) {
            reset();
            Render2D.pushScissor(graphics, x - 1.0F * scale, y - size * 0.25F,
                    availableWidth + 2.0F * scale, size * 1.5F);
            try {
                Render2D.text(FontType.SEMIBOLD, text, x, y, size, color);
            } finally {
                Render2D.popScissor(graphics);
            }
            return;
        }

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Math.min(0.05F, (now - lastNanos) / 1_000_000_000.0F);
        lastNanos = now;
        float cycleWidth = textWidth + 20.0F * scale;
        float step = cycleWidth <= 1.0E-4F ? 0.0F : dt * (20.0F * scale) / cycleWidth;

        if (hovered) {
            active = true;
            returnDirection = 0;
            phase += step;
            if (phase >= 1.0F) {
                phase -= 1.0F;
            }
        } else if (active) {
            if (returnDirection == 0) {
                returnDirection = phase < 0.5F ? -1 : 1;
            }
            if (returnDirection > 0) {
                phase += step;
                if (phase >= 1.0F) {
                    reset();
                }
            } else {
                phase -= step * 2.5F;
                if (phase <= 0.0F) {
                    reset();
                }
            }
        }

        float drawX = x - phase * cycleWidth;
        float fadeWidth = Math.min(12.0F * scale, availableWidth * 0.5F);
        Render2D.pushScissor(graphics, x, y - size * 0.25F, availableWidth, size * 1.5F);
        try {
            Render2D.textFade(FontType.SEMIBOLD, text, drawX, y, size, color,
                    x, x + availableWidth, fadeWidth, false, true);
            Render2D.textFade(FontType.SEMIBOLD, text, drawX + cycleWidth, y, size, color,
                    x, x + availableWidth, fadeWidth, false, true);
        } finally {
            Render2D.popScissor(graphics);
        }
    }
}
