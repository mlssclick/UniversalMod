package universalmod.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ProjectionUtil {
    private ProjectionUtil() {
    }

    public static Vector2f project(Vec3d position) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double fov = mc == null || mc.options == null ? 70.0 : mc.options.getFov().getValue();
        return project(position, fov);
    }

    public static Vector2f project(Vec3d position, double fov) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.getWindow() == null || position == null)
            return new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cp = camera.getPos();
        Quaternionf rotation = new Quaternionf(camera.getRotation()).conjugate();
        Vector3f p = new Vector3f((float) (position.x - cp.x), (float) (position.y - cp.y), (float) (position.z - cp.z));
        p.rotate(rotation);
        if (!Double.isFinite(fov) || fov <= 0.0) fov = 70.0;
        float halfW = mc.getWindow().getScaledWidth() / 2f, halfH = mc.getWindow().getScaledHeight() / 2f;
        float z = p.z();
        if (z >= -0.001f) return new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        float scale = halfH / (-z * (float) Math.tan(Math.toRadians(fov / 2.0)));
        return new Vector2f(p.x() * scale + halfW, -p.y() * scale + halfH);
    }
}
