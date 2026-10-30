package universalmod.render.fireglow;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL20;
import universalmod.render.post.LegacyGl;

public final class FireGlowRenderer {
    private static SimpleFramebuffer before, after;
    private static int program;
    private static boolean captured, failed;

    private FireGlowRenderer() {
    }

    public static void beforeFire(FireGlowState state) {
        if (state == null || !state.enabled() || failed) return;
        try {
            Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
            before = LegacyGl.ensure(before, main.textureWidth, main.textureHeight, false);
            LegacyGl.copy(main, before, false, false);
            captured = true;
            main.beginWrite(false);
        } catch (Throwable t) {
            failed = true;
        }
    }

    public static void afterFire(FireGlowState state) {
        if (!captured || state == null || !state.enabled() || failed) {
            captured = false;
            return;
        }
        captured = false;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Framebuffer main = mc.getFramebuffer();
            after = LegacyGl.ensure(after, main.textureWidth, main.textureHeight, false);
            LegacyGl.copy(main, after, false, false);
            if (program == 0) program = LegacyGl.program(LegacyGl.FULLSCREEN_VS, FS, "fire_glow_1201");
            main.beginWrite(false);
            GL20.glUseProgram(program);
            LegacyGl.bindTexture(0, before.getColorAttachment());
            LegacyGl.bindTexture(1, after.getColorAttachment());
            LegacyGl.i(program, "BeforeSampler", 0);
            LegacyGl.i(program, "AfterSampler", 1);
            LegacyGl.f2(program, "Texel", 1f / main.textureWidth, 1f / main.textureHeight);
            LegacyGl.f3(program, "GlowColor", state.red() / 255f, state.green() / 255f, state.blue() / 255f);
            LegacyGl.i(program, "CustomColor", state.customColor() ? 1 : 0);
            LegacyGl.drawFullscreen(program, main);
            LegacyGl.unbindTextures(2);
            main.beginWrite(false);
        } catch (Throwable t) {
            failed = true;

        }
    }

    public static void reset() {
        LegacyGl.delete(before);
        LegacyGl.delete(after);
        before = after = null;
        LegacyGl.deleteProgram(program);
        program = 0;
        captured = false;
        failed = false;
    }

    private static final String FS = """
            #version 150
             uniform sampler2D BeforeSampler;uniform sampler2D AfterSampler;uniform vec2 Texel;uniform vec3 GlowColor;uniform int CustomColor;in vec2 vUv;out vec4 fragColor;
             float maskAt(vec2 uv){vec3 a=texture(BeforeSampler,uv).rgb,b=texture(AfterSampler,uv).rgb;return smoothstep(.015,.13,length(b-a));}
             void main(){vec4 aft=texture(AfterSampler,vUv);float m=maskAt(vUv);float ring=0.;for(int x=-5;x<=5;x++)for(int y=-5;y<=5;y++){float d=length(vec2(x,y));if(d>5.)continue;ring=max(ring,maskAt(vUv+vec2(x,y)*Texel)*(1.-d/6.));}ring=max(0.,ring-m*.72);vec3 fire=max(aft.rgb-texture(BeforeSampler,vUv).rgb,vec3(0));vec3 autoColor=normalize(fire+vec3(.35,.08,.01));vec3 gc=CustomColor!=0?GlowColor:autoColor;vec3 outc=aft.rgb+gc*ring*1.15;fragColor=vec4(clamp(outc,0.,1.),aft.a);}
            """;
}
