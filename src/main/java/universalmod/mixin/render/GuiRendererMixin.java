package universalmod.mixin.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import universalmod.render.blur.BlurFramebuffer;
import universalmod.render.arc.UiArcRenderer;
import universalmod.render.glass.GlassRenderer;
import universalmod.render.hud.HudSurfaceRenderer;
import universalmod.render.image.ImageRenderer;
import universalmod.render.surface.GuiSurfaceRenderer;
import universalmod.render.shape.OutlineRenderer;
import universalmod.render.glass.GlassOutlineRenderer;
import universalmod.render.shape.RectangleRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    private RenderPass universalmod$currentRenderPass;
    private boolean universalmod$blurDrawActive;
    private boolean universalmod$glassDrawActive;
    private boolean universalmod$hudSurfaceDrawActive;
    private boolean universalmod$glassOutlineDrawActive;
    private boolean universalmod$rectangleDrawActive;
    private boolean universalmod$outlineDrawActive;
    private boolean universalmod$imageDrawActive;
    private boolean universalmod$arcDrawActive;
    private boolean universalmod$guiSurfaceDrawActive;

    @Inject(method = "render", at = @At("HEAD"))
    private void universalmod$beginBlurFrame(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        BlurFramebuffer.getInstance().beginGuiFrame();
        GlassRenderer.getInstance().beginGuiFrame();
        HudSurfaceRenderer.getInstance().beginGuiFrame();
        GlassOutlineRenderer.getInstance().beginGuiFrame();
        RectangleRenderer.getInstance().beginGuiFrame();
        OutlineRenderer.getInstance().beginGuiFrame();
        ImageRenderer.getInstance().beginGuiFrame();
        UiArcRenderer.getInstance().beginGuiFrame();
        GuiSurfaceRenderer.getInstance().beginGuiFrame();
    }

    @Inject(method = "prepare", at = @At("HEAD"))
    private void universalmod$preparePendingBlurResources(CallbackInfo ci) {
        BlurFramebuffer.getInstance().preparePending();
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private void universalmod$prepareRenderUniforms(CallbackInfo ci) {
        BlurFramebuffer.getInstance().prepareBuffers();
        GlassRenderer.getInstance().prepareBuffers();
        HudSurfaceRenderer.getInstance().prepareBuffers();
        GlassOutlineRenderer.getInstance().prepareBuffers();
        RectangleRenderer.getInstance().prepareBuffers();
        OutlineRenderer.getInstance().prepareBuffers();
        ImageRenderer.getInstance().prepareBuffers();
        UiArcRenderer.getInstance().prepareBuffers();
        GuiSurfaceRenderer.getInstance().prepareBuffers();
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void universalmod$prepareBlurCapture(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        BlurFramebuffer.getInstance().prepareGuiDraw();
    }

    @Inject(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V", shift = At.Shift.BEFORE))
    private void universalmod$prepareBlurCaptureAfterBeforeBlur(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        BlurFramebuffer.getInstance().prepareGuiDraw();
    }

    @Redirect(method = "executeDraw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private void universalmod$trackPipeline(RenderPass renderPass, RenderPipeline pipeline) {
        universalmod$currentRenderPass = renderPass;
        universalmod$blurDrawActive = BlurFramebuffer.getInstance().isBlurPipeline(pipeline);
        universalmod$glassDrawActive = GlassRenderer.getInstance().isGlassPipeline(pipeline);
        universalmod$hudSurfaceDrawActive = HudSurfaceRenderer.getInstance().isPipeline(pipeline);
        universalmod$glassOutlineDrawActive = GlassOutlineRenderer.getInstance().isGlassOutlinePipeline(pipeline);
        universalmod$rectangleDrawActive = RectangleRenderer.getInstance().isRectanglePipeline(pipeline);
        universalmod$outlineDrawActive = OutlineRenderer.getInstance().isOutlinePipeline(pipeline);
        universalmod$imageDrawActive = ImageRenderer.getInstance().isImagePipeline(pipeline);
        universalmod$arcDrawActive = UiArcRenderer.getInstance().isPipeline(pipeline);
        universalmod$guiSurfaceDrawActive = GuiSurfaceRenderer.getInstance().isPipeline(pipeline);
        renderPass.setPipeline(pipeline);
    }

    @Inject(method = "executeDraw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V", shift = At.Shift.BEFORE))
    private void universalmod$bindBlurParams(@Coerce Object draw, RenderPass renderPass, GpuBuffer gpuBuffer, VertexFormat.IndexType indexType, CallbackInfo ci) {
        if (universalmod$blurDrawActive && universalmod$currentRenderPass != null) {
            BlurFramebuffer.getInstance().bindBlurParams(universalmod$currentRenderPass);
        }
        if (universalmod$glassDrawActive && universalmod$currentRenderPass != null) {
            GlassRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$hudSurfaceDrawActive && universalmod$currentRenderPass != null) {
            HudSurfaceRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$glassOutlineDrawActive && universalmod$currentRenderPass != null) {
            GlassOutlineRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$rectangleDrawActive && universalmod$currentRenderPass != null) {
            RectangleRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$outlineDrawActive && universalmod$currentRenderPass != null) {
            OutlineRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$imageDrawActive && universalmod$currentRenderPass != null) {
            ImageRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$arcDrawActive && universalmod$currentRenderPass != null) {
            UiArcRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$guiSurfaceDrawActive && universalmod$currentRenderPass != null) {
            GuiSurfaceRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
    }

    @Inject(method = "executeDraw", at = @At("RETURN"))
    private void universalmod$clearTrackedPipeline(@Coerce Object draw, RenderPass renderPass, GpuBuffer gpuBuffer, VertexFormat.IndexType indexType, CallbackInfo ci) {
        universalmod$clearTrackedState();
    }

    private void universalmod$clearTrackedState() {
        universalmod$currentRenderPass = null;
        universalmod$clearTrackedFlags();
    }

    private void universalmod$clearTrackedFlags() {
        universalmod$blurDrawActive = false;
        universalmod$glassDrawActive = false;
        universalmod$hudSurfaceDrawActive = false;
        universalmod$glassOutlineDrawActive = false;
        universalmod$rectangleDrawActive = false;
        universalmod$outlineDrawActive = false;
        universalmod$imageDrawActive = false;
        universalmod$arcDrawActive = false;
        universalmod$guiSurfaceDrawActive = false;
    }
}
