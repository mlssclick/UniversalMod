package universalmod.module.render;

import universalmod.event.SubscribeEvent;

import java.awt.Color;

import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.SliderSetting;
import universalmod.render.world.BlockOverlayShaderRenderer;
import universalmod.render.world.BlockOverlayShaderRenderer.ShaderMode;
import universalmod.render.Render3D;

@ModuleInformation(
        moduleName = "Block Overlay",
        moduleCategory = ModuleCategory.RENDER
)
public final class BlockOverlay extends Module {
    private static final String SHADER_NONE = "None";
    private static final String SHADER_NEBULA = "Nebula";
    private static final String SHADER_COBWEB = "Cobweb";
    private static final String SHADER_PLASMA = "Plasma";
    private static final String SHADER_STARFIELD = "Starfield";
    private static final double SHAPE_EPSILON = 0.001D;

    private static BlockOverlay instance;

    public final BooleanSetting outline = new BooleanSetting("Outline", true);
    public final SliderSetting lineWidth = new SliderSetting("Line Width", 2.5D, 0.5D, 8.0D, 0.1D);
    public final ColorSetting outlineColor = new ColorSetting("Outline Color", new Color(0, 0, 0, 255).getRGB());

    public final BooleanSetting fill = new BooleanSetting("Fill", true);
    public final ModeSetting fillShader = new ModeSetting(
            "Shader", SHADER_NONE,
            SHADER_NONE, SHADER_NEBULA, SHADER_COBWEB, SHADER_PLASMA, SHADER_STARFIELD
    );
    public final ColorSetting fillColor = new ColorSetting("Fill Color", new Color(0, 0, 0, 102).getRGB());
    public final SliderSetting fillOpacity = new SliderSetting("Fill Opacity", 40.0D, 0.0D, 100.0D, 1.0D);
    public final BooleanSetting fillChroma = new BooleanSetting("Fill Chroma", false);
    public final SliderSetting fillSaturation = new SliderSetting("Fill Saturation", 1.0D, 0.0D, 1.0D, 0.01D);
    public final SliderSetting fillBrightness = new SliderSetting("Fill Brightness", 1.0D, 0.0D, 1.0D, 0.01D);
    public final SliderSetting fillSpeed = new SliderSetting("Fill Speed", 0.25D, 0.0D, 1.0D, 0.01D);
    public final ColorSetting nebulaColor = new ColorSetting("Nebula Color", new Color(0, 255, 171, 255).getRGB());
    public final SliderSetting nebulaSpeed = new SliderSetting("Nebula Speed", 1.5D, 0.0D, 4.0D, 0.01D);
    public final ColorSetting cobwebColor = new ColorSetting("Cobweb Color", new Color(234, 234, 240, 255).getRGB());
    public final SliderSetting cobwebSpeed = new SliderSetting("Cobweb Speed", 1.5D, 0.0D, 4.0D, 0.01D);
    public final ColorSetting plasmaColor = new ColorSetting("Plasma Color", new Color(255, 96, 210, 255).getRGB());
    public final SliderSetting plasmaSpeed = new SliderSetting("Plasma Speed", 1.5D, 0.0D, 4.0D, 0.01D);
    public final ColorSetting starfieldColor = new ColorSetting("Starfield Color", new Color(132, 198, 255, 255).getRGB());
    public final SliderSetting starfieldSpeed = new SliderSetting("Starfield Speed", 0.6D, 0.0D, 4.0D, 0.01D);


    private final BlockOverlayShaderRenderer shaderRenderer = new BlockOverlayShaderRenderer();

    public BlockOverlay() {
        instance = this;
        configureVisibility();
    }

    public static BlockOverlay getInstance() {
        return instance;
    }

    public static boolean shouldSuppressVanillaOutline() {
        BlockOverlay module = instance;
        return module != null
                && module.isEnabled()
                && (module.outline.getValue() || module.fill.getValue())
                && module.resolveTarget() != null;
    }

    @Override
    public void onDisable() {
        BlockOverlayShaderRenderer.close();
        super.onDisable();
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        if (!outline.getValue() && !fill.getValue()) {
            return;
        }

        TargetBlock target = resolveTarget();
        RenderTarget renderTarget = target == null ? null : new RenderTarget(
                new Vec3d(target.pos().getX(), target.pos().getY(), target.pos().getZ()), target.shape());
        if (renderTarget == null) {
            return;
        }

        boolean renderThroughWalls = false;
        if (fill.getValue()) {
            ShaderMode shaderMode = ShaderMode.fromSetting(fillShader.getValue());
            if (shaderMode == ShaderMode.NONE) {
                int fillRgba = resolveColor(
                        fillColor.getValue(),
                        fillChroma.getValue(),
                        fillSaturation.getValue(),
                        fillBrightness.getValue(),
                        fillSpeed.getValue()
                );
                fillRgba = applyAlpha(fillRgba, fillOpacity.getFloatValue());
                drawFilledShape(event, renderTarget.origin(), renderTarget.shape(), fillRgba, !renderThroughWalls);
            } else {
                shaderRenderer.renderFill(
                        event.getMatrixStack(),
                        renderTarget.origin(),
                        renderTarget.shape(),
                        shaderMode,
                        new Color(resolveShaderColor(shaderMode), true),
                        1.0f,
                        resolveShaderSpeed(shaderMode),
                        renderThroughWalls
                );
            }
        }

        if (outline.getValue()) {
            drawOutlineShape(
                    renderTarget.origin(),
                    renderTarget.shape(),
                    outlineColor.getValue(),
                    lineWidth.getFloatValue(),
                    !renderThroughWalls
            );
        }
    }

    private void configureVisibility() {
        lineWidth.setVisible(outline::getValue);
        outlineColor.setVisible(outline::getValue);
        fillShader.setVisible(fill::getValue);
        fillColor.setVisible(() -> fill.getValue() && isShaderNoneSelected());
        fillOpacity.setVisible(() -> fill.getValue() && isShaderNoneSelected());
        fillChroma.setVisible(() -> fill.getValue() && isShaderNoneSelected());
        fillSaturation.setVisible(() -> fill.getValue() && fillChroma.getValue() && isShaderNoneSelected());
        fillBrightness.setVisible(() -> fill.getValue() && fillChroma.getValue() && isShaderNoneSelected());
        fillSpeed.setVisible(() -> fill.getValue() && fillChroma.getValue() && isShaderNoneSelected());
        nebulaColor.setVisible(() -> fill.getValue() && SHADER_NEBULA.equals(fillShader.getValue()));
        nebulaSpeed.setVisible(() -> fill.getValue() && SHADER_NEBULA.equals(fillShader.getValue()));
        cobwebColor.setVisible(() -> fill.getValue() && SHADER_COBWEB.equals(fillShader.getValue()));
        cobwebSpeed.setVisible(() -> fill.getValue() && SHADER_COBWEB.equals(fillShader.getValue()));
        plasmaColor.setVisible(() -> fill.getValue() && SHADER_PLASMA.equals(fillShader.getValue()));
        plasmaSpeed.setVisible(() -> fill.getValue() && SHADER_PLASMA.equals(fillShader.getValue()));
        starfieldColor.setVisible(() -> fill.getValue() && SHADER_STARFIELD.equals(fillShader.getValue()));
        starfieldSpeed.setVisible(() -> fill.getValue() && SHADER_STARFIELD.equals(fillShader.getValue()));
    }

    private TargetBlock resolveTarget() {
        if (!canRender()) {
            return null;
        }
        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        VoxelShape shape = state.getOutlineShape(mc.world, pos, ShapeContext.of(mc.player));
        if (shape.isEmpty()) {
            return null;
        }
        return new TargetBlock(pos, shape);
    }

    private boolean canRender() {
        if (!isEnabled() || mc.world == null || mc.player == null || mc.options.hudHidden) {
            return false;
        }
        ClientPlayerInteractionManager manager = mc.interactionManager;
        if (manager == null) {
            return false;
        }
        GameMode gameMode = manager.getCurrentGameMode();
        return gameMode != GameMode.ADVENTURE && gameMode != GameMode.SPECTATOR;
    }

    private void drawFilledShape(WorldRenderEvent event, Vec3d origin, VoxelShape shape, int color, boolean depth) {
        for (Box local : shape.getBoundingBoxes()) {
            Box world = new Box(
                    origin.x + local.minX - SHAPE_EPSILON,
                    origin.y + local.minY - SHAPE_EPSILON,
                    origin.z + local.minZ - SHAPE_EPSILON,
                    origin.x + local.maxX + SHAPE_EPSILON,
                    origin.y + local.maxY + SHAPE_EPSILON,
                    origin.z + local.maxZ + SHAPE_EPSILON
            );
            Render3D.filledBox(event.getMatrixStack(), world, color, depth);
        }
    }

    private void drawOutlineShape(Vec3d origin, VoxelShape shape, int color, float width, boolean depth) {
        for (Box local : shape.getBoundingBoxes()) {
            Box world = new Box(
                    origin.x + local.minX - SHAPE_EPSILON,
                    origin.y + local.minY - SHAPE_EPSILON,
                    origin.z + local.minZ - SHAPE_EPSILON,
                    origin.x + local.maxX + SHAPE_EPSILON,
                    origin.y + local.maxY + SHAPE_EPSILON,
                    origin.z + local.maxZ + SHAPE_EPSILON
            );
            Render3D.boxOutline(world, color, width, depth);
        }
    }

    private static int resolveColor(int baseColor, boolean chroma, double saturation, double brightness, double speed) {
        if (!chroma) {
            return baseColor;
        }
        float hue = (float) ((System.currentTimeMillis() * Math.max(0.0D, speed) * 0.02D) % 360.0D);
        int rgb = Color.HSBtoRGB(
                hue / 360.0F,
                MathHelper.clamp((float) saturation, 0.0F, 1.0F),
                MathHelper.clamp((float) brightness, 0.0F, 1.0F)
        );
        return (baseColor & 0xFF000000) | (rgb & 0x00FFFFFF);
    }

    private static int applyAlpha(int color, float opacityPercent) {
        int alpha = Math.round(MathHelper.clamp(opacityPercent, 0.0F, 100.0F) * 2.55F);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private boolean isShaderNoneSelected() {
        return SHADER_NONE.equals(fillShader.getValue());
    }

    private int resolveShaderColor(ShaderMode mode) {
        return switch (mode) {
            case NEBULA -> nebulaColor.getValue();
            case COBWEB -> cobwebColor.getValue();
            case PLASMA -> plasmaColor.getValue();
            case STARFIELD -> starfieldColor.getValue();
            case NONE -> fillColor.getValue();
        };
    }

    private float resolveShaderSpeed(ShaderMode mode) {
        return switch (mode) {
            case NEBULA -> nebulaSpeed.getFloatValue();
            case COBWEB -> cobwebSpeed.getFloatValue();
            case PLASMA -> plasmaSpeed.getFloatValue();
            case STARFIELD -> starfieldSpeed.getFloatValue();
            case NONE -> fillSpeed.getFloatValue();
        };
    }

    private record TargetBlock(BlockPos pos, VoxelShape shape) {
    }

    private record RenderTarget(Vec3d origin, VoxelShape shape) {
    }
}
