package universalmod.mixin.render;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;

@Mixin(GameRenderer.class)
public abstract class GameRendererNoRenderMixin {
    @Inject(method = "showFloatingItem(Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$hideTotemAnimation(ItemStack stack, CallbackInfo ci) {
        if (stack != null && stack.isOf(Items.TOTEM_OF_UNDYING) && NoRender.isActive("Totem Animation")) {
            ci.cancel();
        }
    }
}
