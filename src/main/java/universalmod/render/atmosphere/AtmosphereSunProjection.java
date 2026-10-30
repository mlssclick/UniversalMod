package universalmod.render.atmosphere;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class AtmosphereSunProjection {
    private AtmosphereSunProjection() {}

    public record Sun(float x, float y, float z,
                      float u, float v, float onScreen,
                      float reversedDepth) {}

    public static Sun sample(Camera camera, float partialTick, Matrix4f view, Matrix4f projection) {
        float angle = camera.attributeProbe().getValue(
                EnvironmentAttributes.SUN_ANGLE, partialTick) * Mth.DEG_TO_RAD;
        float sx = -Mth.sin(angle);
        float sy = Mth.cos(angle);
        float sz = 0.0f;
        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        Vector4f projected = new Vector4f(sx, sy, sz, 0.0f).mul(viewProjection);
        float u = 0.5f;
        float v = 0.5f;
        float visibility = 0.0f;
        if (projected.w > 0.0001f) {
            u = projected.x / projected.w * 0.5f + 0.5f;
            v = projected.y / projected.w * 0.5f + 0.5f;
            
            
            if (sy > -0.05f && u > -0.2f && u < 1.2f && v > -0.2f && v < 1.2f) {
                visibility = 1.0f;
            }
        }
        
        float reversed = projection.m22() > -0.5f ? 1.0f : 0.0f;
        return new Sun(sx, sy, sz, u, v, visibility, reversed);
    }
}
