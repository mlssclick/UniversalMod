package universalmod.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Animations;

@Mixin(InGameHud.class)
public abstract class InGameHudAnimationsMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void universalmod$renderClosingTab(DrawContext context, float tickDelta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Animations.active(Animations.TAB) || mc.world == null || mc.options.playerListKey.isPressed() || Animations.getInstance().tabProgress() <= .001f)
            return;
        Scoreboard scoreboard = mc.world.getScoreboard();
        mc.inGameHud.getPlayerListHud().render(context, mc.getWindow().getScaledWidth(), scoreboard, scoreboard.getObjectiveForSlot(Scoreboard.LIST_DISPLAY_SLOT_ID));
    }
}
