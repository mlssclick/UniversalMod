package universalmod.render.fireglow;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import universalmod.render.ClientPipelines;
import universalmod.render.post.Saturation2D;






public final class FireGlowRenderer {
    private static SimpleFramebuffer before;
    private static SimpleFramebuffer mask;
    private static SimpleFramebuffer sobel;
    private static SimpleFramebuffer blurH;
    private static SimpleFramebuffer blurV;
    private static boolean captured;
    private static boolean disabledAfterError;

    private FireGlowRenderer() {
    }

    public static void captureBefore(FireGlowState state) {
        captured = false;
        if (state == null || !state.enabled() || disabledAfterError) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client == null ? null : client.getFramebuffer();
        if (client == null || client.player == null || main == null || main.textureWidth <= 0 || main.textureHeight <= 0)
            return;
        try {
            ensure(main.textureWidth, main.textureHeight);
            before.beginWrite(true);
            main.draw(before.textureWidth, before.textureHeight);
            main.beginWrite(true);
            captured = true;
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void compositeAfter(FireGlowState state) {
        if (!captured || state == null || !state.enabled() || disabledAfterError) {
            captured = false;
            return;
        }
        captured = false;
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client == null ? null : client.getFramebuffer();
        if (main == null || before == null) return;
        try {
            ensure(main.textureWidth, main.textureHeight);
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.disableBlend();


            mask.beginWrite(true);
            RenderSystem.setShaderTexture(0, before.getColorAttachment());
            RenderSystem.setShaderTexture(1, main.getColorAttachment());
            ShaderProgram maskShader = ClientPipelines.bind(ClientPipelines.FIRE_MASK, "render_fire_glow_mask");
            if (maskShader == null) return;
            var useCustom = maskShader.getUniform("UseCustomColor");
            if (useCustom != null) useCustom.set(state.customColor() ? 1.0F : 0.0F);
            var custom = maskShader.getUniform("CustomColor");
            if (custom != null) custom.set(state.red() / 255.0F, state.green() / 255.0F, state.blue() / 255.0F);
            Saturation2D.drawFullscreen();


            sobel.beginWrite(true);
            RenderSystem.setShaderTexture(0, mask.getColorAttachment());
            ShaderProgram sobelShader = ClientPipelines.bind(ClientPipelines.FIRE_SOBEL, "render_fire_glow_sobel");
            setTexel(sobelShader, main.textureWidth, main.textureHeight);
            Saturation2D.drawFullscreen();


            blurH.beginWrite(true);
            RenderSystem.setShaderTexture(0, sobel.getColorAttachment());
            ShaderProgram blurShader = ClientPipelines.bind(ClientPipelines.FIRE_BLUR, "render_fire_glow_blur");
            setTexel(blurShader, main.textureWidth, main.textureHeight);
            setDirection(blurShader, 1.0F, 0.0F);
            Saturation2D.drawFullscreen();

            blurV.beginWrite(true);
            RenderSystem.setShaderTexture(0, blurH.getColorAttachment());
            blurShader = ClientPipelines.bind(ClientPipelines.FIRE_BLUR, "render_fire_glow_blur");
            setTexel(blurShader, main.textureWidth, main.textureHeight);
            setDirection(blurShader, 0.0F, 1.0F);
            Saturation2D.drawFullscreen();


            main.beginWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, blurV.getColorAttachment());
            ShaderProgram composite = ClientPipelines.bind(ClientPipelines.FIRE_COMPOSITE, "render_fire_glow_composite");
            if (composite != null) Saturation2D.drawFullscreen();
        } catch (Throwable t) {
            fail(t);
        } finally {
            if (main != null) main.beginWrite(true);
            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.setShaderTexture(1, 0);
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
        }
    }

    private static void setTexel(ShaderProgram shader, int width, int height) {
        if (shader == null) return;
        var u = shader.getUniform("OneTexel");
        if (u != null) u.set(1.0F / Math.max(width, 1), 1.0F / Math.max(height, 1));
    }

    private static void setDirection(ShaderProgram shader, float x, float y) {
        if (shader == null) return;
        var u = shader.getUniform("BlurDir");
        if (u != null) u.set(x, y);
    }

    private static void ensure(int width, int height) {
        before = Saturation2D.ensure(before, width, height, false);
        mask = Saturation2D.ensure(mask, width, height, false);
        sobel = Saturation2D.ensure(sobel, width, height, false);
        blurH = Saturation2D.ensure(blurH, width, height, false);
        blurV = Saturation2D.ensure(blurV, width, height, false);
    }

    private static void fail(Throwable throwable) {
        disabledAfterError = true;


        close();
    }

    public static void reloadResources() {
        disabledAfterError = false;
        close();
    }

    public static void close() {
        captured = false;
        SimpleFramebuffer[] all = {before, mask, sobel, blurH, blurV};
        for (SimpleFramebuffer f : all) if (f != null) f.delete();
        before = mask = sobel = blurH = blurV = null;
    }
}
