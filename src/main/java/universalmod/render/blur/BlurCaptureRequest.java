package universalmod.render.blur;

import org.joml.Matrix4f;

record BlurCaptureRequest(BlurPass blur, Matrix4f pose, BlurRegion scissorArea) {
    BlurCaptureRequest {
        if (blur == null || pose == null) throw new IllegalArgumentException("Missing capture geometry");
        pose = new Matrix4f(pose);
    }
}
