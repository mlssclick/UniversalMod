package universalmod.mixin.render;

import net.minecraft.client.render.FrameGraphBuilder;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.Handle;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.integration.minecraft.IMinecraft;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.render.Ambience;
import universalmod.module.render.Atmosphere;
import universalmod.module.render.BlockOverlay;
import universalmod.module.render.NoRender;
import universalmod.lifecycle.Manager;
import universalmod.render.Render3D;
import universalmod.render.blur.BlurFramebuffer;
import universalmod.render.wetworld.WetWorldRenderer;
import universalmod.render.winter.WinterWorldRenderer;
import universalmod.render.atmosphere.AtmosphereMoodRenderer;
import universalmod.render.atmosphere.SakuraWorldRenderer;
import universalmod.render.atmosphere.WeatherWorldRenderer;
import universalmod.render.atmosphere.AtmosphereRainRenderer;
import universalmod.render.world.ambience.SkyShaderRenderer;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin implements IMinecraft {

    @Shadow @Final private MinecraftClient client;
        @Shadow private ClientWorld world;

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$removeClouds(CallbackInfo ci) {
        if (NoRender.isActive("Clouds")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$removeWeather(CallbackInfo ci) {
        if (NoRender.isActive("Weather")) {
            ci.cancel();
        }
    }

    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void universalmod$replaceBlockOutline(CallbackInfo ci) {
        if (BlockOverlay.shouldSuppressVanillaOutline() || NoRender.isActive("Block Overlay")) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        universalmod.render.RenderCompatibility.primeFromCurrentContext();
        MatrixStack captureStack = new MatrixStack();
        captureStack.multiplyPositionMatrix(new Matrix4f(positionMatrix));
        
        
        SkyShaderRenderer.beginFrame();
        Render3D.lastProjMat.set(projectionMatrix);
        Render3D.lastModMat.set(positionMatrix);
        Render3D.lastWorldSpaceMatrix.set(captureStack.peek().getPositionMatrix());
        Render3D.setLastWorldSpaceEntry(captureStack.peek());
        Render3D.setLastTickDelta(tickCounter.getTickDelta(true));
        Render3D.setLastCameraPos(camera.getPos());
        Render3D.setLastCameraRotation(new Quaternionf(camera.getRotation()));

    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderWorld(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        Atmosphere atmosphere = Atmosphere.getInstance();
        float delta = tickCounter.getTickDelta(true);
        if (atmosphere != null && atmosphere.isWinterActive()) {
            WinterWorldRenderer.render(atmosphere, camera, positionMatrix, projectionMatrix, delta);
        } else if (atmosphere != null && atmosphere.isMoodActive()) {
            AtmosphereMoodRenderer.render(atmosphere, camera, positionMatrix, projectionMatrix, delta);
        }
        if (atmosphere != null && (atmosphere.isSakuraActive() || atmosphere.isAutumnActive())) {
            SakuraWorldRenderer.render(atmosphere, camera, positionMatrix, delta);
        }
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && ambience.isWetActive()
                && (atmosphere == null || !atmosphere.isThunderActive())) {
            WetWorldRenderer.render(camera, positionMatrix, projectionMatrix, delta);
        }
        if (atmosphere != null && atmosphere.isRainActive()) {
            AtmosphereRainRenderer.render(atmosphere, camera, positionMatrix, projectionMatrix, delta);
            WeatherWorldRenderer.render(atmosphere, camera, positionMatrix, delta);
        }
        MatrixStack stack = new MatrixStack();
        stack.multiplyPositionMatrix(new Matrix4f(positionMatrix));

        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int prevDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int prevBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int prevBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int prevBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int prevBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int[] prevScissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, prevScissorBox);

        try {
            WorldRenderEvent event = new WorldRenderEvent(stack, tickCounter.getTickDelta(true));
            Manager.postEvent(event);
            Render3D.onWorldRender(event);
        } finally {
            restoreRenderState(
                    prevDepthTest,
                    prevBlend,
                    prevCull,
                    prevScissor,
                    prevDepthMask,
                    prevDepthFunc,
                    prevBlendSrcRgb,
                    prevBlendDstRgb,
                    prevBlendSrcAlpha,
                    prevBlendDstAlpha,
                    prevScissorBox
            );
        }
    }

    private void restoreRenderState(boolean depthTest, boolean blend, boolean cull, boolean scissor, boolean depthMask, int depthFunc, int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha, int[] scissorBox) {
        if (depthTest) {
            GlStateManager._enableDepthTest();
        } else {
            GlStateManager._disableDepthTest();
        }
        GlStateManager._depthMask(depthMask);
        GlStateManager._depthFunc(depthFunc);
        GlStateManager._colorMask(true, true, true, true);

        if (blend) {
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        } else {
            GlStateManager._disableBlend();
        }

        if (cull) {
            GlStateManager._enableCull();
        } else {
            GlStateManager._disableCull();
        }

        if (scissor) {
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        } else {
            GlStateManager._disableScissorTest();
        }
    }
}
