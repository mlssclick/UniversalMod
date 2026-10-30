package universalmod.render.blur;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import universalmod.render.Render2DCoordinateSpace;

import java.util.List;


final class BlurRegionPlanner {
    private BlurRegionPlanner() {
    }

    static Result compute(
            List<BlurCaptureRequest> requests,
            int guiWidth,
            int guiHeight,
            int targetWidth,
            int targetHeight
    ) {
        if (requests == null || requests.isEmpty() || guiWidth <= 0 || guiHeight <= 0
                || targetWidth <= 0 || targetHeight <= 0) {
            return null;
        }

        float scaleX = (float) targetWidth / guiWidth;
        float scaleY = (float) targetHeight / guiHeight;
        float minX = guiWidth;
        float minY = guiHeight;
        float maxX = 0.0F;
        float maxY = 0.0F;
        float maxBlurRadius = 0.0F;
        float maxKawaseRadius = 0.0F;
        float maxScoreboardRadius = 0.0F;
        boolean kawase = false;
        boolean scoreboard = false;
        boolean hasBounds = false;

        for (BlurCaptureRequest request : requests) {
            BlurPass blur = request.blur();
            float padding = Render2DCoordinateSpace.toGui(Math.max(16.0F, blur.blurRadius() * 2.0F));
            ScreenRectangle bounds = new ScreenRectangle(
                    Math.round(blur.x()),
                    Math.round(blur.y()),
                    Math.round(blur.width()),
                    Math.round(blur.height())
            ).transformMaxBounds(request.pose());
            if (request.scissorArea() != null) {
                bounds = request.scissorArea().intersection(bounds);
                if (bounds == null) {
                    continue;
                }
            }

            minX = Math.min(minX, bounds.left() - padding);
            minY = Math.min(minY, bounds.top() - padding);
            maxX = Math.max(maxX, bounds.right() + padding);
            maxY = Math.max(maxY, bounds.bottom() + padding);
            maxBlurRadius = Math.max(maxBlurRadius, blur.blurRadius());
            if (blur.algorithm() == BlurAlgorithm.SCOREBOARD_SEPARABLE) {
                scoreboard = true;
                maxScoreboardRadius = Math.max(maxScoreboardRadius, blur.blurRadius());
            } else {
                kawase = true;
                maxKawaseRadius = Math.max(maxKawaseRadius, blur.blurRadius());
            }
            hasBounds = true;
        }
        if (!hasBounds) {
            return null;
        }

        minX = clamp(minX, 0.0F, guiWidth);
        minY = clamp(minY, 0.0F, guiHeight);
        maxX = clamp(maxX, minX + 1.0F, guiWidth);
        maxY = clamp(maxY, minY + 1.0F, guiHeight);

        int x0 = clampInt((int) Math.floor(minX * scaleX), 0, targetWidth - 1);
        int x1 = clampInt((int) Math.ceil(maxX * scaleX), x0 + 1, targetWidth);
        int y0 = clampInt((int) Math.floor((guiHeight - maxY) * scaleY), 0, targetHeight - 1);
        int y1 = clampInt((int) Math.ceil((guiHeight - minY) * scaleY), y0 + 1, targetHeight);
        int width = Math.max(x1 - x0, 1);
        int height = Math.max(y1 - y0, 1);
        return new Result(
                x0, y0, width, height,
                maxBlurRadius, maxKawaseRadius, maxScoreboardRadius,
                kawase, scoreboard
        );
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record Result(
            int x,
            int y,
            int width,
            int height,
            float maxBlurRadius,
            float maxKawaseRadius,
            float maxScoreboardRadius,
            boolean kawase,
            boolean scoreboard
    ) {
    }
}
