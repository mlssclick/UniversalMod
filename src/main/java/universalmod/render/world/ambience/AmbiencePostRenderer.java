package universalmod.render.world.ambience;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL20;
import universalmod.render.post.LegacyGl;

public final class AmbiencePostRenderer {
    private static SimpleFramebuffer scene;
    private static int program;
    private static boolean failed;

    private AmbiencePostRenderer() {
    }

    public static void resetTime() {
        SkyShaderRenderer.resetTime();
    }

    public static void apply(float saturation) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || failed) return;
        if (Math.abs(saturation - 1f) <= .0005f) return;
        try {
            Framebuffer main = mc.getFramebuffer();
            scene = LegacyGl.ensure(scene, main.textureWidth, main.textureHeight, false);
            LegacyGl.copy(main, scene, false, false);
            if (program == 0) program = LegacyGl.program(LegacyGl.FULLSCREEN_VS, FS, "ambience_saturation_1201");
            main.beginWrite(false);
            RenderSystem.disableDepthTest();
            GL20.glUseProgram(program);
            LegacyGl.bindTexture(0, scene.getColorAttachment());
            LegacyGl.i(program, "SceneSampler", 0);
            LegacyGl.f(program, "uSaturation", Math.max(0f, Math.min(2f, saturation)));
            LegacyGl.drawFullscreen(program, main);
            LegacyGl.unbindTextures(1);
            main.beginWrite(false);
        } catch (Throwable t) {
            failed = true;

        }
    }

    public static void clear() {
        LegacyGl.delete(scene);
        scene = null;
        LegacyGl.deleteProgram(program);
        program = 0;
        failed = false;
    }

    private static final String FS = """
            #version 150
            uniform sampler2D SceneSampler;uniform float uSaturation;in vec2 vUv;out vec4 fragColor;
            void main(){vec4 base=texture(SceneSampler,vUv);float l=dot(base.rgb,vec3(.2126,.7152,.0722));fragColor=vec4(clamp(mix(vec3(l),base.rgb,uSaturation),0.,1.),base.a);}
            """;
}
