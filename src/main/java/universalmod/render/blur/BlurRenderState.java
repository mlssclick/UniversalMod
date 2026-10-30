package universalmod.render.blur;

import org.joml.Matrix4f;

public record BlurRenderState(Matrix4f pose, BlurPass blur) {
}
