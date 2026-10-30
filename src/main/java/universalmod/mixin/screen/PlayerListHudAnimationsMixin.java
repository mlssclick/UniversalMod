package universalmod.mixin.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Animations;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudAnimationsMixin {
    @Unique
    private boolean universalmod$tabMatricesPushed;

    @Inject(method = "setVisible", at = @At("HEAD"), require = 0)
    private void universalmod$animateVisibility(boolean visible, CallbackInfo ci) {
        if (Animations.active(Animations.TAB)) {
            Animations.getInstance().setTabVisible(visible);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void universalmod$beginTabAnimation(
            DrawContext context,
            int width,
            Scoreboard scoreboard,
            ScoreboardObjective objective,
            CallbackInfo ci
    ) {
        universalmod$tabMatricesPushed = false;
        if (!Animations.active(Animations.TAB)) {
            return;
        }

        float progress = Animations.getInstance().tabProgress();
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, -200.0F * (1.0F - progress), 0.0F);
        universalmod$tabMatricesPushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void universalmod$endTabAnimation(
            DrawContext context,
            int width,
            Scoreboard scoreboard,
            ScoreboardObjective objective,
            CallbackInfo ci
    ) {
        if (universalmod$tabMatricesPushed) {
            context.getMatrices().pop();
            universalmod$tabMatricesPushed = false;
        }
    }
}
