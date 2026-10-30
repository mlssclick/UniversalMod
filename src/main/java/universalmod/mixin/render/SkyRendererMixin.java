package universalmod.mixin.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Ambience;
import universalmod.module.render.Atmosphere;
import universalmod.render.world.ambience.SkyShaderRenderer;
import universalmod.render.world.ambience.SkyShaderState;

@Mixin(WorldRenderer.class)
public abstract class SkyRendererMixin {
    @Inject(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
    private void universalmod$renderSkyShader(MatrixStack matrices, Matrix4f projection, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        Atmosphere atmosphere = Atmosphere.getInstance();
        if (atmosphere != null && (atmosphere.isWinterActive() || atmosphere.isMoodActive())) {
            if (SkyShaderRenderer.renderAtmosphere(atmosphere, new Matrix4f(matrices.peek().getPositionMatrix()), projection))
                ci.cancel();
            return;
        }
        Ambience ambience = Ambience.getInstance();
        SkyShaderState state = ambience == null ? null : ambience.skyShaderState();
        if (state != null && state.enabled() && camera != null && camera.getSubmersionType() == CameraSubmersionType.NONE && SkyShaderRenderer.render(state, new Matrix4f(matrices.peek().getPositionMatrix()), projection))
            ci.cancel();
    }
}
