package universalmod.mixin.render;

import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.utility.CustomDonate;
import universalmod.module.utility.TotemCounter;
import universalmod.module.render.ping.PingNametags;
import universalmod.module.render.ping.PingNametagHelper;

@Mixin(DisplayEntityRenderer.TextDisplayEntityRenderer.class)
public abstract class TextDisplayEntityRendererMixin {
    @Inject(method = "getData(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;)Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$Data;", at = @At("RETURN"), cancellable = true)
    private void universalmod$rewritePlayerNametag(DisplayEntity.TextDisplayEntity entity, CallbackInfoReturnable<DisplayEntity.TextDisplayEntity.Data> cir) {
        DisplayEntity.TextDisplayEntity.Data data = cir.getReturnValue();
        if (data == null || !(entity.getVehicle() instanceof PlayerEntity player)) return;
        if (!player.isAlive()) {
            TotemCounter.remove(player);
            return;
        }
        if (!TotemCounter.isActive() && !CustomDonate.isActive() && !PingNametags.isActive()) return;
        Text source = data.text();
        if (source == null || source.getString().isBlank()) return;
        String playerName = player.getEntityName();
        if (playerName == null || playerName.isBlank() || !source.getString().contains(playerName)) return;
        Text modified = CustomDonate.replaceDonate(player, source);
        modified = TotemCounter.appendCounter(player, modified);
        modified = PingNametagHelper.appendPing(player, modified);
        if (modified == source || modified.equals(source)) return;
        cir.setReturnValue(new DisplayEntity.TextDisplayEntity.Data(modified, data.lineWidth(), data.textOpacity(), data.backgroundColor(), data.flags()));
    }
}
