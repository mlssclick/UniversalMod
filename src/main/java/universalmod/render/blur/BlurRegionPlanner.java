package universalmod.render.blur;

import org.joml.Vector3f;

import java.util.List;

final class BlurRegionPlanner {
    private BlurRegionPlanner() { }

    static Result compute(List<BlurCaptureRequest> requests,
                          int guiWidth, int guiHeight, int targetWidth, int targetHeight,
                          float guiIndependentScale) {
        if (requests == null || requests.isEmpty() || guiWidth <= 0 || guiHeight <= 0
                || targetWidth <= 0 || targetHeight <= 0 || !Float.isFinite(guiIndependentScale)
                || guiIndependentScale <= 0) return null;
        float sx = (float) targetWidth / guiWidth;
        float sy = (float) targetHeight / guiHeight;
        float minX = guiWidth, minY = guiHeight, maxX = 0, maxY = 0;
        float maxRadius = 0, maxKawase = 0, maxScoreboard = 0;
        boolean kawase = false, scoreboard = false, found = false;
        Vector3f point = new Vector3f();

        for (BlurCaptureRequest request : requests) {
            BlurPass blur = request.blur();
            if (!blur.visible() || !Float.isFinite(blur.x()) || !Float.isFinite(blur.y())
                    || !Float.isFinite(blur.width()) || !Float.isFinite(blur.height())
                    || !Float.isFinite(blur.blurRadius())) continue;
            float l = Float.POSITIVE_INFINITY, t = Float.POSITIVE_INFINITY;
            float r = Float.NEGATIVE_INFINITY, b = Float.NEGATIVE_INFINITY;

            int baseX = Math.round(blur.x());
            int baseY = Math.round(blur.y());
            int width = Math.round(blur.width());
            int height = Math.round(blur.height());
            for (int yi = 0; yi <= 1; yi++) {
                for (int xi = 0; xi <= 1; xi++) {
                    request.pose().transformPosition(baseX + xi * width,
                            baseY + yi * height, 0, point);
                    if (!Float.isFinite(point.x) || !Float.isFinite(point.y)) continue;
                    l = Math.min(l, point.x);
                    t = Math.min(t, point.y);
                    r = Math.max(r, point.x);
                    b = Math.max(b, point.y);
                }
            }
            if (!Float.isFinite(l) || !Float.isFinite(t)
                    || !Float.isFinite(r) || !Float.isFinite(b)) continue;
            BlurRegion scissors = request.scissorArea();
            if (scissors != null) {
                float sLeft = scissors.x() / sx;
                float sRight = (scissors.x() + scissors.width()) / sx;
                float sTop = guiHeight - (scissors.y() + scissors.height()) / sy;
                float sBottom = guiHeight - scissors.y() / sy;
                l = Math.max(l, sLeft);
                r = Math.min(r, sRight);
                t = Math.max(t, sTop);
                b = Math.min(b, sBottom);
                if (r <= l || b <= t) continue;
            }

            float padding = Math.max(16.0f, blur.blurRadius() * 2.0f) * guiIndependentScale;
            minX = Math.min(minX, l - padding);
            minY = Math.min(minY, t - padding);
            maxX = Math.max(maxX, r + padding);
            maxY = Math.max(maxY, b + padding);
            maxRadius = Math.max(maxRadius, blur.blurRadius());
            if (blur.algorithm() == BlurAlgorithm.SCOREBOARD_SEPARABLE) {
                scoreboard = true;
                maxScoreboard = Math.max(maxScoreboard, blur.blurRadius());
            } else {
                kawase = true;
                maxKawase = Math.max(maxKawase, blur.blurRadius());
            }
            found = true;
        }
        if (!found) return null;
        minX = clamp(minX, 0, guiWidth);
        minY = clamp(minY, 0, guiHeight);
        maxX = clamp(maxX, minX + 1, guiWidth);
        maxY = clamp(maxY, minY + 1, guiHeight);
        int x0 = clamp((int) Math.floor(minX * sx), 0, targetWidth - 1);
        int x1 = clamp((int) Math.ceil(maxX * sx), x0 + 1, targetWidth);
        int y0 = clamp((int) Math.floor((guiHeight - maxY) * sy), 0, targetHeight - 1);
        int y1 = clamp((int) Math.ceil((guiHeight - minY) * sy), y0 + 1, targetHeight);
        return new Result(new BlurRegion(x0, y0, x1 - x0, y1 - y0),
                maxRadius, maxKawase, maxScoreboard, kawase, scoreboard);
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    record Result(BlurRegion region, float maxBlurRadius, float maxKawaseRadius,
                  float maxScoreboardRadius, boolean kawase, boolean scoreboard) { }
}
