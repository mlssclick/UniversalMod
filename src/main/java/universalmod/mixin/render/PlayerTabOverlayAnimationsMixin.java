package universalmod.mixin.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Animations;

@Mixin(PlayerListHud.class)
public abstract class PlayerTabOverlayAnimationsMixin {
    @Unique
    private boolean universalmod$tabPosePushed;

    @Inject(method = "setVisible", at = @At("HEAD"), require = 0)
    private void universalmod$animateTabVisibility(boolean visible, CallbackInfo ci) {
        if (!Animations.active(Animations.TAB)) {
            return;
        }
        Animations.getInstance().setTabVisible(visible);
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void universalmod$beginTabAnimation(DrawContext graphics, int width, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        universalmod$tabPosePushed = false;
        if (!Animations.active(Animations.TAB)) {
            return;
        }
        float progress = Animations.getInstance().tabProgress();
        graphics.getMatrices().push();
        graphics.getMatrices().translate(0.0F, -200.0F * (1.0F - progress), 0);
        universalmod$tabPosePushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void universalmod$endTabAnimation(DrawContext graphics, int width, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        if (universalmod$tabPosePushed) {
            graphics.getMatrices().pop();
            universalmod$tabPosePushed = false;
        }
    }
}
