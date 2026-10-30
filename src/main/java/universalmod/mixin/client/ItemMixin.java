package universalmod.mixin.client;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Item.class)
public abstract class ItemMixin {
    @Redirect(method = "raycast", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getPitch()F"), require = 0)
    private static float universalmod$itemPitch(PlayerEntity player) {
        return player.getPitch();
    }

    @Redirect(method = "raycast", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"), require = 0)
    private static float universalmod$itemYaw(PlayerEntity player) {
        return player.getYaw();
    }
}
