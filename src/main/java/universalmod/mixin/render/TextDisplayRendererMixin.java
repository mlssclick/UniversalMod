package universalmod.mixin.render;

import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.client.render.entity.state.TextDisplayEntityRenderState;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.mixin.accessor.TextDisplayEntityRendererAccessor;
import universalmod.module.render.ping.PingNametags;
import universalmod.module.utility.CustomDonate;
import universalmod.module.render.ping.PingNametagHelper;
import universalmod.module.utility.TotemCounter;








@Mixin(DisplayEntityRenderer.TextDisplayEntityRenderer.class)
public abstract class TextDisplayRendererMixin {
    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;Lnet/minecraft/client/render/entity/state/TextDisplayEntityRenderState;F)V",
            at = @At("RETURN"),
            require = 0
    )
    private void universalmod$replaceUniversalNametag(
            DisplayEntity.TextDisplayEntity entity,
            TextDisplayEntityRenderState state,
            float tickDelta,
            CallbackInfo ci
    ) {
        if ((!TotemCounter.isActive() && !CustomDonate.isActive() && !PingNametags.isActive())
                || state.data == null
                || !(entity.getVehicle() instanceof PlayerEntity player)) {
            return;
        }

        if (!player.isAlive()) {
            TotemCounter.remove(player);
            return;
        }

        Text original = state.data.text();
        if (original == null) {
            return;
        }

        String line = original.getString();
        if (line.isBlank() || !line.contains(player.getNameForScoreboard())) {
            return;
        }

        Text modified = CustomDonate.replaceDonate(player, original);
        modified = TotemCounter.appendCounter(player, modified);
        modified = PingNametagHelper.appendPing(player, modified);

        DisplayEntity.TextDisplayEntity.Data data = state.data;
        DisplayEntity.TextDisplayEntity.Data replacedData = new DisplayEntity.TextDisplayEntity.Data(
                modified,
                data.lineWidth(),
                data.textOpacity(),
                data.backgroundColor(),
                data.flags()
        );

        state.data = replacedData;
        state.textLines = ((TextDisplayEntityRendererAccessor) (Object) this)
                .universalmod$invokeGetLines(modified, data.lineWidth());
    }
}
