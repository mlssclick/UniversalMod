package universalmod.module.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;
import universalmod.render.Render3D;
import universalmod.render.world.BlockOverlayShaderRenderer;
import universalmod.render.world.BlockOverlayShaderRenderer.ShaderMode;

import java.awt.Color;

public final class BlockOverlay extends Module {
    private static final double SHAPE_EPSILON = 0.001D;
    private static BlockOverlay instance;
    private final BooleanSetting outline = register(new BooleanSetting("Outline", "Draw block outline.", true));
    private final NumberSetting lineWidth = register(new NumberSetting("Line Width", "Outline line width.", 2.5, .5, 8, .1));
    private final ColorSetting outlineColor = register(new ColorSetting("Outline Color", "Outline color.", 0xFF000000));
    private final BooleanSetting fill = register(new BooleanSetting("Fill", "Draw block fill.", true));
    private final ModeSetting fillShader = register(new ModeSetting("Shader", "Fill shader.", true, "None", "None", "Nebula", "Cobweb", "Plasma", "Starfield"));
    private final ColorSetting fillColor = register(new ColorSetting("Fill Color", "Fill color.", 0x66000000));
    private final NumberSetting fillOpacity = register(new NumberSetting("Fill Opacity", "Non-shader fill opacity.", 40, 0, 100, 1));
    private final BooleanSetting fillChroma = register(new BooleanSetting("Fill Chroma", "Animate fill color.", false));
    private final NumberSetting fillSaturation = register(new NumberSetting("Fill Saturation", "Fill chroma saturation.", 1, 0, 1, .01));
    private final NumberSetting fillBrightness = register(new NumberSetting("Fill Brightness", "Fill chroma brightness.", 1, 0, 1, .01));
    private final NumberSetting fillSpeed = register(new NumberSetting("Fill Speed", "Fill chroma speed.", .25, 0, 1, .01));
    private final ColorSetting nebulaColor = register(new ColorSetting("Nebula Color", "Nebula shader tint.", 0xFF00FFAB));
    private final NumberSetting nebulaSpeed = register(new NumberSetting("Nebula Speed", "Nebula shader speed.", 1.5, 0, 4, .01));
    private final ColorSetting cobwebColor = register(new ColorSetting("Cobweb Color", "Cobweb shader tint.", 0xFFEAEAF0));
    private final NumberSetting cobwebSpeed = register(new NumberSetting("Cobweb Speed", "Cobweb shader speed.", 1.5, 0, 4, .01));
    private final ColorSetting plasmaColor = register(new ColorSetting("Plasma Color", "Plasma shader tint.", 0xFFFF60D2));
    private final NumberSetting plasmaSpeed = register(new NumberSetting("Plasma Speed", "Plasma shader speed.", 1.5, 0, 4, .01));
    private final ColorSetting starfieldColor = register(new ColorSetting("Starfield Color", "Starfield shader tint.", 0xFF84C6FF));
    private final NumberSetting starfieldSpeed = register(new NumberSetting("Starfield Speed", "Starfield shader speed.", .6, 0, 4, .01));
    private final BlockOverlayShaderRenderer shaderRenderer = new BlockOverlayShaderRenderer();

    public BlockOverlay() {
        super("Block Overlay", ModuleCategory.RENDER);
        instance = this;
        visibility();
    }

    public static boolean shouldSuppressVanillaOutline(MinecraftClient client) {
        return instance != null && instance.isEnabled() && (instance.outline.getValue() || instance.fill.getValue()) && instance.target(client) != null;
    }

    @Override
    protected void onDisable() {
        BlockOverlayShaderRenderer.close();
    }

    @SubscribeEvent
    private void render(WorldRenderEvent event) {
        if (!outline.getValue() && !fill.getValue()) return;
        Target target = target(MinecraftClient.getInstance());
        if (target == null) return;

        Vec3d origin = Vec3d.of(target.pos());
        VoxelShape shape = target.shape();
        boolean renderThroughWalls = false;
        if (fill.getValue()) {
            ShaderMode mode = ShaderMode.fromSetting(fillShader.getValue());
            if (mode == ShaderMode.NONE) {
                int color = resolveFill();
                for (Box local : shape.getBoundingBoxes()) {
                    Render3D.drawFilledBox(expandedWorldBox(origin, local), color, !renderThroughWalls);
                }
            } else {
                
                
                shaderRenderer.renderFill(event.getStack(), origin,
                        shape.getBoundingBoxes().stream().map(BlockOverlay::expandedLocalBox).toList(),
                        mode, shaderColor(mode), 1.0F, shaderSpeed(mode), renderThroughWalls);
            }
        }
        if (outline.getValue()) {
            for (Box local : shape.getBoundingBoxes()) {
                Render3D.drawBoxOutline(expandedWorldBox(origin, local),
                        outlineColor.getValue(), lineWidth.getFloat(), !renderThroughWalls);
            }
        }
    }

    private static Box expandedLocalBox(Box box) {
        return box.expand(SHAPE_EPSILON);
    }

    private static Box expandedWorldBox(Vec3d origin, Box box) {
        return expandedLocalBox(box).offset(origin);
    }

    private void visibility() {
        lineWidth.visibleWhen(outline::getValue);
        outlineColor.visibleWhen(outline::getValue);
        fillShader.visibleWhen(fill::getValue);
        fillColor.visibleWhen(() -> fill.getValue() && fillShader.is("None"));
        fillOpacity.visibleWhen(() -> fill.getValue() && fillShader.is("None"));
        fillChroma.visibleWhen(() -> fill.getValue() && fillShader.is("None"));
        fillSaturation.visibleWhen(() -> fill.getValue() && fillChroma.getValue() && fillShader.is("None"));
        fillBrightness.visibleWhen(() -> fill.getValue() && fillChroma.getValue() && fillShader.is("None"));
        fillSpeed.visibleWhen(() -> fill.getValue() && fillChroma.getValue() && fillShader.is("None"));
        nebulaColor.visibleWhen(() -> fill.getValue() && fillShader.is("Nebula"));
        nebulaSpeed.visibleWhen(() -> fill.getValue() && fillShader.is("Nebula"));
        cobwebColor.visibleWhen(() -> fill.getValue() && fillShader.is("Cobweb"));
        cobwebSpeed.visibleWhen(() -> fill.getValue() && fillShader.is("Cobweb"));
        plasmaColor.visibleWhen(() -> fill.getValue() && fillShader.is("Plasma"));
        plasmaSpeed.visibleWhen(() -> fill.getValue() && fillShader.is("Plasma"));
        starfieldColor.visibleWhen(() -> fill.getValue() && fillShader.is("Starfield"));
        starfieldSpeed.visibleWhen(() -> fill.getValue() && fillShader.is("Starfield"));
    }

    private Target target(MinecraftClient c) {
        if (!isEnabled() || c == null || c.world == null || c.player == null || c.options.hudHidden || c.interactionManager == null)
            return null;
        GameMode gm = c.interactionManager.getCurrentGameMode();
        if (gm == GameMode.ADVENTURE || gm == GameMode.SPECTATOR) return null;
        HitResult hr = c.crosshairTarget;
        if (!(hr instanceof BlockHitResult hit) || hr.getType() != HitResult.Type.BLOCK) return null;
        BlockPos p = hit.getBlockPos();
        BlockState s = c.world.getBlockState(p);
        VoxelShape shape = s.getOutlineShape(c.world, p, ShapeContext.of(c.player));
        return shape.isEmpty() ? null : new Target(p, shape);
    }

    private int resolveFill() {
        int base = fillColor.getValue();
        int rgb = base & 0xFFFFFF;
        if (fillChroma.getValue()) {
            float hue = (float) ((System.currentTimeMillis() * Math.max(0, fillSpeed.getValue()) * .02) % 360) / 360f;
            rgb = Color.HSBtoRGB(hue, fillSaturation.getFloat(), fillBrightness.getFloat()) & 0xFFFFFF;
        }
        int a = Math.round(MathHelper.clamp(fillOpacity.getFloat(), 0, 100) * 2.55f);
        return (a << 24) | rgb;
    }

    private int shaderColor(ShaderMode m) {
        return switch (m) {
            case NEBULA -> nebulaColor.getValue();
            case COBWEB -> cobwebColor.getValue();
            case PLASMA -> plasmaColor.getValue();
            case STARFIELD -> starfieldColor.getValue();
            default -> fillColor.getValue();
        }
                ;
    }

    private float shaderSpeed(ShaderMode m) {
        return switch (m) {
            case NEBULA -> nebulaSpeed.getFloat();
            case COBWEB -> cobwebSpeed.getFloat();
            case PLASMA -> plasmaSpeed.getFloat();
            case STARFIELD -> starfieldSpeed.getFloat();
            default -> fillSpeed.getFloat();
        }
                ;
    }

    private record Target(BlockPos pos, VoxelShape shape) {
    }
}
