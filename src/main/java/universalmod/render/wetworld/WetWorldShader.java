package universalmod.render.wetworld;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

public final class WetWorldShader {
    private static final ShaderProgramKey PROGRAM_KEY = new ShaderProgramKey(
            Identifier.of("universalmod", "core/wet_world/data"),
            VertexFormats.POSITION_TEXTURE_COLOR,
            Defines.EMPTY
    );

    private final ScalableFramebuffer framebuffer = new ScalableFramebuffer(false).enableLinearFilter();

    public void close() {
        framebuffer.delete();
    }

    public void render(UniformData data) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.getDepthAttachment() == 0) {
            return;
        }

        int scaledWidth = client.getWindow().getScaledWidth();
        int scaledHeight = client.getWindow().getScaledHeight();
        if (scaledWidth <= 0 || scaledHeight <= 0) {
            return;
        }

        int depthAttachment = main.getDepthAttachment();

        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.backupProjectionMatrix();

        Matrix4f ortho = new Matrix4f().setOrtho(
                0.0f,
                scaledWidth,
                scaledHeight,
                0.0f,
                1000.0f,
                21000.0f
        );
        RenderSystem.setProjectionMatrix(ortho, ProjectionType.ORTHOGRAPHIC);

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity().translate(0.0f, 0.0f, -11000.0f);

        boolean mainReading = false;
        boolean framebufferReading = false;
        boolean framebufferBound = false;
        try {
            framebuffer.bind(false);
            framebufferBound = true;

            ShaderProgram program = RenderSystem.setShader(PROGRAM_KEY);
            if (program == null) {
                return;
            }
            setUniforms(program, data);

            main.beginRead();
            mainReading = true;
            RenderSystem.setShaderTexture(0, main.getColorAttachment());
            RenderSystem.setShaderTexture(1, depthAttachment);
            drawTexturedQuad(0.0f, 0.0f, scaledWidth, scaledHeight);
            main.endRead();
            mainReading = false;

            framebuffer.unbind();
            framebufferBound = false;

            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            framebuffer.beginRead();
            framebufferReading = true;
            RenderSystem.setShaderTexture(0, framebuffer.getColorAttachment());
            drawTexturedQuad(0.0f, 0.0f, scaledWidth, scaledHeight);
            framebuffer.endRead();
            framebufferReading = false;
        } finally {
            if (mainReading) {
                main.endRead();
            }
            if (framebufferReading) {
                framebuffer.endRead();
            }
            if (framebufferBound) {
                framebuffer.unbind();
            }

            RenderSystem.setShaderTexture(1, 0);
            RenderSystem.setShaderTexture(0, 0);
            modelViewStack.popMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void setUniforms(ShaderProgram program, UniformData data) {
        set(program, "ViewProj", data.viewProjection);
        set(program, "InvViewProj", data.inverseViewProjection);
        set(program, "CamPos", data.cameraX, data.cameraY, data.cameraZ);
        set(program, "SkyTint", data.skyRed, data.skyGreen, data.skyBlue);
        set(program, "SunDir", data.sunX, data.sunY, data.sunZ);
        set(program, "Reflectivity", data.reflectivity);
        set(program, "Wetness", data.wetness);
        set(program, "Ripple", 0.35f);
        set(program, "Gloss", data.gloss);
        set(program, "Steps", data.steps);
        set(program, "HitThickness", 0.98f);
        set(program, "MaxDistance", 24.0f);
        set(program, "UpOnly", 1.0f);
        set(program, "Time", data.time);
    }

    private static void set(ShaderProgram program, String name, Matrix4f value) {
        GlUniform uniform = program.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void set(ShaderProgram program, String name, float value) {
        GlUniform uniform = program.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void set(ShaderProgram program, String name, float x, float y, float z) {
        GlUniform uniform = program.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z);
        }
    }

    private static void drawTexturedQuad(float x, float y, float width, float height) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR
        );
        int white = 0xFFFFFFFF;

        builder.vertex(x, y, 0.0f).texture(0.0f, 1.0f).color(white);
        builder.vertex(x, y + height, 0.0f).texture(0.0f, 0.0f).color(white);
        builder.vertex(x + width, y + height, 0.0f).texture(1.0f, 0.0f).color(white);
        builder.vertex(x + width, y, 0.0f).texture(1.0f, 1.0f).color(white);

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public static final class UniformData {
        public final Matrix4f viewProjection = new Matrix4f();
        public final Matrix4f inverseViewProjection = new Matrix4f();
        public float cameraX;
        public float cameraY;
        public float cameraZ;
        public float skyRed = 0.45f;
        public float skyGreen = 0.6f;
        public float skyBlue = 0.9f;
        public float sunX;
        public float sunY = 1.0f;
        public float sunZ;
        public float reflectivity = 0.7f;
        public float wetness = 0.6f;
        public float gloss = 0.5f;
        public float steps = 24.0f;
        public float time;
    }
}
