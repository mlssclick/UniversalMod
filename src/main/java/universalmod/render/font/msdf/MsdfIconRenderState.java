package universalmod.render.font.msdf;

import org.joml.Matrix4f;
import universalmod.render.font.FontType;

public record MsdfIconRenderState(Matrix4f pose, FontType font, String text, float x, float y, float size, int color) {
}
