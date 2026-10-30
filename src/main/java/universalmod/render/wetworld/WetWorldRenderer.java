package universalmod.render.wetworld;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import universalmod.module.render.Ambience;
import universalmod.module.render.Atmosphere;

public final class WetWorldRenderer {
    private static final WetWorldShader SHADER = new WetWorldShader();
    private static final WetWorldShader.UniformData UNIFORMS = new WetWorldShader.UniformData();

    private static long epoch = System.currentTimeMillis();
    private WetWorldRenderer() {}

    public static void resetTime() { epoch = System.currentTimeMillis(); }

    public static void close() {
        SHADER.close();
    }

    public static void render(Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, float tickDelta) {
        Ambience module = Ambience.getInstance();
        Atmosphere wetAtmosphere = Atmosphere.getInstance();
        if (wetAtmosphere != null && wetAtmosphere.isThunderActive()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (module == null || !module.isWetActive() || client.world == null || camera == null || !camera.isReady()) return;

        UNIFORMS.viewProjection.set(projectionMatrix).mul(positionMatrix);
        UNIFORMS.inverseViewProjection.set(UNIFORMS.viewProjection).invert();

        Vec3d cameraPos = camera.getPos();
        UNIFORMS.cameraX = (float) cameraPos.x;
        UNIFORMS.cameraY = (float) cameraPos.y;
        UNIFORMS.cameraZ = (float) cameraPos.z;

        int skyColor = client.world.getSkyColor(cameraPos, tickDelta);
        UNIFORMS.skyRed = ((skyColor >> 16) & 255) / 255.0f;
        UNIFORMS.skyGreen = ((skyColor >> 8) & 255) / 255.0f;
        UNIFORMS.skyBlue = (skyColor & 255) / 255.0f;

        boolean hasSkyLight = client.world.getDimension().hasSkyLight();
        float skyAngle = client.world.getSkyAngleRadians(tickDelta);
        UNIFORMS.sunX = -MathHelper.sin(skyAngle);
        UNIFORMS.sunY = MathHelper.cos(skyAngle);
        UNIFORMS.sunZ = 0.0f;
        UNIFORMS.reflectivity = module.wetReflectionStrength();
        UNIFORMS.wetness = module.wetness();
        UNIFORMS.gloss = hasSkyLight ? 0.45f : 0.0f;
        UNIFORMS.steps = module.wetQualitySteps();
        UNIFORMS.time = (System.currentTimeMillis() - epoch) / 1000.0f;
        SHADER.render(UNIFORMS);
    }
}
