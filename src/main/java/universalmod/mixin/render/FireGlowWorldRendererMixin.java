package universalmod.mixin.render;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.FireGlow;
import universalmod.render.fireglow.FireGlowFramebuffer;

@Mixin(WorldRenderer.class)
public abstract class FireGlowWorldRendererMixin {
    private static final Identifier VANILLA_ENTITY_OUTLINE=Identifier.ofVanilla("entity_outline");
    @Inject(method="render",at=@At("HEAD"))
    private void universalmod$beginFireGlowFrame(CallbackInfo ci) {
        if(FireGlow.isActive())FireGlowFramebuffer.beginFrame();
    }
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gl/ShaderLoader;loadPostEffect(Lnet/minecraft/util/Identifier;Ljava/util/Set;)Lnet/minecraft/client/gl/PostEffectProcessor;"))
    private PostEffectProcessor universalmod$selectOutlinePass(ShaderLoader loader,Identifier id,Set<Identifier> targets) {
        if(FireGlow.isActive() && VANILLA_ENTITY_OUTLINE.equals(id))return null;
        return loader.loadPostEffect(id,targets);
    }
    @Inject(method="drawEntityOutlinesFramebuffer",at=@At("HEAD"),cancellable=true)
    private void universalmod$deferOutlineComposite(CallbackInfo ci) {
        if(FireGlow.isActive())ci.cancel();
    }
}
