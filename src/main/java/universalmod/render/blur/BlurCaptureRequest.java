package universalmod.render.blur;

import org.joml.Matrix4f;
import universalmod.render.ScissorStack;

 





record BlurCaptureRequest(
        float x, float y, float width, float height,
        float blurRadius, BlurAlgorithm algorithm,
        Matrix4f pose, ScissorStack.FramebufferBounds scissor
) {
    BlurCaptureRequest {
        pose = pose == null ? new Matrix4f() : new Matrix4f(pose);
        algorithm = algorithm == null ? BlurAlgorithm.KAWASE : algorithm;
    }
}
