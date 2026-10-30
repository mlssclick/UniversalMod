package universalmod.render;

import java.io.IOException;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class UiShaders {
    public static ShaderProgram ARC;
    public static ShaderProgram RECT;
    public static ShaderProgram SMOOTH_ROUNDED;
    public static ShaderProgram OUTLINE;
    public static ShaderProgram MSDF;
    public static ShaderProgram IMAGE;
    public static ShaderProgram BLUR;
    public static ShaderProgram GLASS;
    public static ShaderProgram GLASS_OUTLINE;
    public static ShaderProgram KAWASE_DOWN;
    public static ShaderProgram KAWASE_UP;
    public static ShaderProgram SCOREBOARD_SEPARABLE;
    public static ShaderProgram HUD_CHROME;
    public static ShaderProgram EFFECT_ICON;
    public static ShaderProgram BLOCK_OVERLAY;
    public static ShaderProgram GUI_SURFACE;
    public static ShaderProgram MEDIA_BACKGROUND;
    public static ShaderProgram MEDIA_SHADOW;
    public static ShaderProgram WORLD_LYRICS_MSDF;

    private UiShaders() {
    }

    public static void registerShaders(CoreShaderRegistrationCallback.RegistrationContext context) throws IOException {
        universalmod.render.image.ImageRenderer.releaseResources();
        universalmod.render.blur.ScoreboardBlurRenderer.close();
        universalmod.render.blur.KawaseBlurRenderer.close();
        register(context, "ui_arc", VertexFormats.POSITION_TEXTURE_COLOR, shader -> ARC = shader);
        register(context, "ui_rect", VertexFormats.POSITION_TEXTURE_COLOR, shader -> RECT = shader);
        register(context, "ui_smooth_rounded", VertexFormats.POSITION_TEXTURE_COLOR, shader -> SMOOTH_ROUNDED = shader);
        register(context, "ui_outline", VertexFormats.POSITION_TEXTURE_COLOR, shader -> OUTLINE = shader);
        register(context, "msdf", VertexFormats.POSITION_TEXTURE_COLOR, shader -> MSDF = shader);
        register(context, "world_lyrics_msdf", VertexFormats.POSITION_TEXTURE_COLOR, shader -> WORLD_LYRICS_MSDF = shader);
        register(context, "ui_image", VertexFormats.POSITION_TEXTURE_COLOR, shader -> IMAGE = shader);
        register(context, "ui_blur", VertexFormats.POSITION_TEXTURE_COLOR, shader -> BLUR = shader);
        register(context, "ui_glass", VertexFormats.POSITION_TEXTURE_COLOR, shader -> GLASS = shader);
        register(context, "ui_glass_outline", VertexFormats.POSITION_TEXTURE_COLOR, shader -> GLASS_OUTLINE = shader);
        register(context, "kawase_down", VertexFormats.POSITION_COLOR, shader -> KAWASE_DOWN = shader);
        register(context, "kawase_up", VertexFormats.POSITION_COLOR, shader -> KAWASE_UP = shader);
        register(context, "ui_scoreboard_separable", VertexFormats.POSITION_COLOR, shader -> SCOREBOARD_SEPARABLE = shader);
        register(context, "hud_chrome", VertexFormats.POSITION_TEXTURE_COLOR, shader -> HUD_CHROME = shader);
        register(context, "effect_icon", VertexFormats.POSITION_TEXTURE_COLOR, shader -> EFFECT_ICON = shader);
        register(context, "block_overlay", VertexFormats.POSITION_COLOR, shader -> BLOCK_OVERLAY = shader);
        register(context, "ui_surface", VertexFormats.POSITION_TEXTURE_COLOR, shader -> GUI_SURFACE = shader);
        register(context, "ui_media_background", VertexFormats.POSITION_COLOR, shader -> MEDIA_BACKGROUND = shader);
        register(context, "ui_media_shadow", VertexFormats.POSITION_COLOR, shader -> MEDIA_SHADOW = shader);
    }

    private static void register(CoreShaderRegistrationCallback.RegistrationContext context, String name, VertexFormat format, java.util.function.Consumer<ShaderProgram> consumer) throws IOException {
        context.register(new Identifier("universalmod", name), format, consumer);
    }

    public static ShaderProgram bind(ShaderProgram shader, String name) {
        if (shader == null) {
            ShaderDiagnostics.missing(name);
            return null;
        }
        com.mojang.blaze3d.systems.RenderSystem.setShader(() -> shader);
        return shader;
    }

    public static boolean ready() {
        return RECT != null
                && OUTLINE != null
                && MSDF != null
                && IMAGE != null
                && BLUR != null
                && GLASS != null
                && GLASS_OUTLINE != null
                && KAWASE_DOWN != null
                && KAWASE_UP != null
                && HUD_CHROME != null
                && EFFECT_ICON != null;
    }
}
