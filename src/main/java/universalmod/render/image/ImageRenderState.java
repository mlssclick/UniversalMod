package universalmod.render.image;

import org.joml.Matrix4f;

public record ImageRenderState(Matrix4f pose, BuiltImage image, ImageTexture texture) {
}
