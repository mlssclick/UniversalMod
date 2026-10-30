package universalmod.mixin.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.render.color.ColorUtil;
import universalmod.render.font.FontType;
import universalmod.render.Render2D;
import universalmod.theme.ThemeColors;
import universalmod.module.misc.ShadersButton;

@Mixin(PressableWidget.class)
public abstract class GameMenuButtonMixin {
    @Unique
    private static final float UNIVERSALMOD_ANIMATION_SPEED = 0.28F;

    @Unique
    private float universalmod$hoverAnimation;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void universalmod$renderGameMenuButton(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ShadersButton.isActive()
                || client == null
                || !(client.currentScreen instanceof GameMenuScreen)
                || context == null) {
            return;
        }

        PressableWidget button = (PressableWidget) (Object) this;
        boolean hovered = button.isHovered() && button.active;
        universalmod$hoverAnimation = universalmod$approach(
                universalmod$hoverAnimation,
                hovered ? 1.0F : 0.0F,
                UNIVERSALMOD_ANIMATION_SPEED
        );
        float hover = universalmod$cubicOut(universalmod$hoverAnimation);

        float x = button.getX();
        float y = button.getY();
        float width = button.getWidth();
        float height = button.getHeight();
        float radius = Math.min(5.0F, height * 0.28F);
        int theme = universalmod$themeColor();

        Render2D.beginFrame(context);
        try {
            int shadowAlpha = button.active
                    ? Math.round(80.0F + 35.0F * hover)
                    : 45;
            Render2D.blur(
                    x,
                    y,
                    width,
                    height,
                    radius,
                    8.0F,
                    1.0F,
                    ColorUtil.rgba(0, 0, 0, shadowAlpha)
            );

            int background = ColorUtil.interpolateColor(
                    ColorUtil.rgba(16, 16, 21, button.active ? 205 : 145),
                    ColorUtil.rgba(27, 27, 35, 235),
                    hover
            );
            Render2D.rect(x, y, width, height, radius, background);

            int border = button.active
                    ? ColorUtil.multAlpha(theme, 0.28F + hover * 0.62F)
                    : ColorUtil.rgba(95, 95, 105, 70);
            Render2D.outline(x, y, width, height, radius, 0.45F, border);

            String label = button.getMessage().getString();
            float textSize = 7.5F;
            int textColor = button.active
                    ? ColorUtil.interpolateColor(
                    ColorUtil.rgba(205, 205, 214, 235),
                    ColorUtil.rgba(255, 255, 255, 255),
                    hover
            )
                    : ColorUtil.rgba(135, 135, 145, 170);

            float textX = x + (width - Render2D.textWidth(FontType.SEMIBOLD, label, textSize)) * 0.5F;
            float textY = y
                    + (height - Render2D.textHeight(FontType.SEMIBOLD, label, textSize)) * 0.5F
                    + 0.5F;
            Render2D.text(FontType.SEMIBOLD, label, textX, textY, textSize, textColor);
        } finally {
            Render2D.flush();
        }

        ci.cancel();
    }

    @Unique
    private static float universalmod$approach(float current, float target, float speed) {
        float clampedSpeed = Math.max(0.0F, Math.min(1.0F, speed));
        return current + (target - current) * clampedSpeed;
    }

    @Unique
    private static float universalmod$cubicOut(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value)) - 1.0F;
        return t * t * t + 1.0F;
    }

    @Unique
    private static int universalmod$themeColor() {
        return ColorUtil.withAlpha(ThemeColors.clickGuiSliderFillColor(1.0F), 255);
    }
}
