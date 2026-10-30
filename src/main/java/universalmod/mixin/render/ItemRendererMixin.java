package universalmod.mixin.render;

import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import universalmod.module.render.ItemReplacer;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @ModifyVariable(method = "getModel", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private ItemStack universalmod$replaceSwordModel(ItemStack stack) {
        return ItemReplacer.applyStatic(stack);
    }
}
