package universalmod.render.blur;

import org.joml.Matrix4f;
import universalmod.render.ScissorStack;

import java.util.List;

 
final class BlurScreenRegion {
    record Bounds(int x, int y, int width, int height) {}

    private BlurScreenRegion() {}

    static Bounds compute(Matrix4f matrix, float x, float y, float width, float height,
                          float blurRadius, int guiWidth, int guiHeight,
                          int framebufferWidth, int framebufferHeight) {
        BlurCaptureRequest request = new BlurCaptureRequest(x, y, width, height, blurRadius,
                BlurAlgorithm.SCOREBOARD_SEPARABLE, matrix, ScissorStack.currentFramebufferBounds());
        BlurRegionPlanner.Result result = BlurRegionPlanner.compute(List.of(request), guiWidth, guiHeight,
                framebufferWidth, framebufferHeight);
        return result == null ? null : new Bounds(result.x(), result.y(), result.width(), result.height());
    }
}
