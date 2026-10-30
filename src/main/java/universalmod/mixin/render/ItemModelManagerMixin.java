package universalmod.mixin.render;

import net.minecraft.client.item.ItemModelManager;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import universalmod.module.render.ItemReplacer;

@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {
    @ModifyVariable(
            method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0
    )
    private ItemStack universalmod$replaceSwordModelLeftHand(ItemStack stack) {
        return replace(stack);
    }

    @ModifyVariable(
            method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0
    )
    private ItemStack universalmod$replaceSwordModel(ItemStack stack) {
        return replace(stack);
    }

    private static ItemStack replace(ItemStack stack) {
        ItemReplacer replacer = ItemReplacer.getInstance();
        return replacer == null ? stack : replacer.apply(stack);
    }
}
