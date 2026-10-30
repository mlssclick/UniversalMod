package universalmod.render.blur;

import org.joml.Vector3f;
import universalmod.render.ScissorStack;

import java.util.List;

 









final class BlurRegionPlanner {
    private BlurRegionPlanner() {}

    static Result compute(List<BlurCaptureRequest> requests,
                          int guiWidth, int guiHeight,
                          int targetWidth, int targetHeight) {
        if (requests == null || requests.isEmpty() || guiWidth <= 0 || guiHeight <= 0
                || targetWidth <= 0 || targetHeight <= 0) return null;

        float scaleX = (float) targetWidth / guiWidth;
        float scaleY = (float) targetHeight / guiHeight;
        double unionLeft = Double.POSITIVE_INFINITY;
        double unionBottom = Double.POSITIVE_INFINITY;
        double unionRight = Double.NEGATIVE_INFINITY;
        double unionTop = Double.NEGATIVE_INFINITY;
        float maxRadius = 0;
        float maxKawase = 0;
        float maxScoreboard = 0;
        boolean needsKawase = false;
        boolean needsScoreboard = false;
        Vector3f vertex = new Vector3f();

        for (BlurCaptureRequest request : requests) {
            if (request == null || !Float.isFinite(request.x()) || !Float.isFinite(request.y())
                    || !Float.isFinite(request.width()) || !Float.isFinite(request.height())
                    || request.width() <= 0 || request.height() <= 0) continue;

            double left = Double.POSITIVE_INFINITY, bottom = Double.POSITIVE_INFINITY;
            double right = Double.NEGATIVE_INFINITY, top = Double.NEGATIVE_INFINITY;
            boolean valid = true;
            for (int i = 0; i < 4; i++) {
                float x = request.x() + ((i & 1) != 0 ? request.width() : 0);
                float y = request.y() + (i >= 2 ? request.height() : 0);
                request.pose().transformPosition(x, y, 0, vertex);
                if (!Float.isFinite(vertex.x) || !Float.isFinite(vertex.y)) {
                    valid = false;
                    break;
                }
                double px = vertex.x * scaleX;
                double py = (guiHeight - vertex.y) * scaleY;
                left = Math.min(left, px);
                right = Math.max(right, px);
                bottom = Math.min(bottom, py);
                top = Math.max(top, py);
            }
            if (!valid || left >= right || bottom >= top) continue;

            ScissorStack.FramebufferBounds clip = request.scissor();
            if (clip != null) {
                if (clip.width() <= 0 || clip.height() <= 0) continue;
                left = Math.max(left, clip.x());
                bottom = Math.max(bottom, clip.y());
                right = Math.min(right, (double) clip.x() + clip.width());
                top = Math.min(top, (double) clip.y() + clip.height());
                if (left >= right || bottom >= top) continue;
            }

            
            
            
            float radius = Float.isFinite(request.blurRadius()) ? Math.max(0, request.blurRadius()) : 0;
            
            
            double padding = Math.max(16.0, radius * 2.0);
            unionLeft = Math.min(unionLeft, left - padding);
            unionBottom = Math.min(unionBottom, bottom - padding);
            unionRight = Math.max(unionRight, right + padding);
            unionTop = Math.max(unionTop, top + padding);
            maxRadius = Math.max(maxRadius, radius);
            if (request.algorithm() == BlurAlgorithm.SCOREBOARD_SEPARABLE) {
                needsScoreboard = true;
                maxScoreboard = Math.max(maxScoreboard, radius);
            } else {
                needsKawase = true;
                maxKawase = Math.max(maxKawase, radius);
            }
        }

        if (!needsKawase && !needsScoreboard) return null;
        int x0 = clamp((int) Math.floor(unionLeft), 0, targetWidth);
        int y0 = clamp((int) Math.floor(unionBottom), 0, targetHeight);
        int x1 = clamp((int) Math.ceil(unionRight), 0, targetWidth);
        int y1 = clamp((int) Math.ceil(unionTop), 0, targetHeight);
        if (x1 <= x0 || y1 <= y0) return null;
        return new Result(x0, y0, x1 - x0, y1 - y0,
                maxRadius, maxKawase, maxScoreboard, needsKawase, needsScoreboard);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record Result(int x, int y, int width, int height,
                  float maxBlurRadius, float maxKawaseRadius, float maxScoreboardRadius,
                  boolean kawase, boolean scoreboard) {}
}
