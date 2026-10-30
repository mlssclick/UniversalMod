package universalmod.render.effect;

import org.joml.Matrix4f;

public record EffectIconRenderState(Matrix4f pose, BuiltEffectIcon icon, EffectIconTexture texture) {
}
