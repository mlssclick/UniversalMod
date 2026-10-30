package universalmod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;









public final class ClientPipelines {

    public static final ShaderProgramKey RECTANGLE = positionColor("ui_rect_default");
    public static final ShaderProgramKey OUTLINE = positionColor("ui_outline_default");
    public static final ShaderProgramKey MSDF_ICON = textureColor("ui_msdf_icon");
    public static final ShaderProgramKey WORLD_LYRICS_MSDF = textureColor("world_lyrics_msdf");
    public static final ShaderProgramKey IMAGE = textureColor("ui_image");
    public static final ShaderProgramKey BLUR = positionColor("ui_batched_blur");
    public static final ShaderProgramKey SCOREBOARD_CROP = positionColor("ui_scoreboard_crop");
    public static final ShaderProgramKey SCOREBOARD_SEPARABLE = positionColor("ui_scoreboard_separable");
    public static final ShaderProgramKey GLASS = positionColor("ui_glass");
    public static final ShaderProgramKey GLASS_OUTLINE = positionColor("ui_outline_glass");
    public static final ShaderProgramKey KAWASE_DOWN = positionColor("ui_kawase_down");
    public static final ShaderProgramKey KAWASE_UP = positionColor("ui_kawase_up");
    public static final ShaderProgramKey HUD_CHROME = positionColor("ui_hud_chrome");
    public static final ShaderProgramKey HUD_SURFACE = positionColor("ui_hud_surface");
    public static final ShaderProgramKey GUI_SURFACE = positionColor("ui_surface");
    public static final ShaderProgramKey EFFECT_ICON = textureColor("ui_effect_icon");
    public static final ShaderProgramKey MEDIA_BACKGROUND = positionColor("ui_media_background");
    public static final ShaderProgramKey MEDIA_SHADOW = positionColor("ui_media_shadow");


    public static final ShaderProgramKey BLOCK_NEBULA = positionColor("render_block_overlay_nebula");
    public static final ShaderProgramKey BLOCK_COBWEB = positionColor("render_block_overlay_cobweb");
    public static final ShaderProgramKey BLOCK_PLASMA = positionColor("render_block_overlay_plasma");
    public static final ShaderProgramKey BLOCK_STARFIELD = positionColor("render_block_overlay_starfield");

    public static final ShaderProgramKey SKY_GENERIC = positionColor("render_sky");
    public static final ShaderProgramKey SKY_AURORA = positionColor("render_sky_aurora");
    public static final ShaderProgramKey SKY_NORTHERN = positionColor("render_sky_northern");
    public static final ShaderProgramKey SKY_NEBULA = positionColor("render_sky_nebula");
    public static final ShaderProgramKey SKY_PLASMA = positionColor("render_sky_plasma");

    public static final ShaderProgramKey SATURATION = positionColor("render_saturation");

    public static final ShaderProgramKey FIRE_MASK = positionColor("render_fire_glow_mask");
    public static final ShaderProgramKey FIRE_SOBEL = positionColor("render_fire_glow_sobel");
    public static final ShaderProgramKey FIRE_BLUR = positionColor("render_fire_glow_blur");
    public static final ShaderProgramKey FIRE_COMPOSITE = positionColor("render_fire_glow_composite");


    public static final ShaderProgramKey HEALTH_ICON = textureColor("render_health_icon");

    private static final Map<String, ShaderProgramKey> PROGRAMS = new LinkedHashMap<>();
    private static boolean preloadAttempted;

    static {
        register("ui_rect_default", RECTANGLE);
        register("ui_outline_default", OUTLINE);
        register("ui_msdf_icon", MSDF_ICON);
        register("world_lyrics_msdf", WORLD_LYRICS_MSDF);
        register("ui_image", IMAGE);
        register("ui_batched_blur", BLUR);
        register("ui_scoreboard_crop", SCOREBOARD_CROP);
        register("ui_scoreboard_separable", SCOREBOARD_SEPARABLE);
        register("ui_glass", GLASS);
        register("ui_outline_glass", GLASS_OUTLINE);
        register("ui_kawase_down", KAWASE_DOWN);
        register("ui_kawase_up", KAWASE_UP);
        register("ui_hud_chrome", HUD_CHROME);
        register("ui_hud_surface", HUD_SURFACE);
        register("ui_surface", GUI_SURFACE);
        register("ui_effect_icon", EFFECT_ICON);
        register("ui_media_background", MEDIA_BACKGROUND);
        register("ui_media_shadow", MEDIA_SHADOW);

        register("render_block_overlay_nebula", BLOCK_NEBULA);
        register("render_block_overlay_cobweb", BLOCK_COBWEB);
        register("render_block_overlay_plasma", BLOCK_PLASMA);
        register("render_block_overlay_starfield", BLOCK_STARFIELD);
        register("render_sky", SKY_GENERIC);
        register("render_sky_aurora", SKY_AURORA);
        register("render_sky_northern", SKY_NORTHERN);
        register("render_sky_nebula", SKY_NEBULA);
        register("render_sky_plasma", SKY_PLASMA);
        register("render_saturation", SATURATION);
        register("render_fire_glow_mask", FIRE_MASK);
        register("render_fire_glow_sobel", FIRE_SOBEL);
        register("render_fire_glow_blur", FIRE_BLUR);
        register("render_fire_glow_composite", FIRE_COMPOSITE);
        register("render_health_icon", HEALTH_ICON);
    }

    private ClientPipelines() {
    }

    public static synchronized void ensurePreloaded() {
        if (preloadAttempted) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getResourceManager() == null || client.getShaderLoader() == null) {
            return;
        }
        preloadAttempted = true;
        for (Map.Entry<String, ShaderProgramKey> entry : PROGRAMS.entrySet()) {
            preload(entry.getKey(), entry.getValue(), false);
        }
    }

    public static ShaderProgram bind(ShaderProgramKey key, String name) {
        ensurePreloaded();
        ShaderProgram program = RenderSystem.setShader(key);
        if (program != null) {
            return program;
        }
        preload(name, key, true);
        return RenderSystem.setShader(key);
    }

    public static void resetPreloadState() {
        preloadAttempted = false;
    }

    private static void preload(String name, ShaderProgramKey key, boolean report) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getResourceManager() == null || client.getShaderLoader() == null) {
            return;
        }
        try {
            client.getShaderLoader().preload(client.getResourceManager(), new ShaderProgramKey[]{key});
        } catch (Throwable throwable) {
            if (report) {


            }
        }
    }

    private static void register(String name, ShaderProgramKey key) {
        PROGRAMS.put(name, key);
    }

    private static ShaderProgramKey positionColor(String name) {
        return key(name, VertexFormats.POSITION_COLOR);
    }

    private static ShaderProgramKey textureColor(String name) {
        return key(name, VertexFormats.POSITION_TEXTURE_COLOR);
    }

    private static ShaderProgramKey key(String name, VertexFormat format) {
        return new ShaderProgramKey(Identifier.of("universalmod", "core/" + name), format, Defines.EMPTY);
    }
}
