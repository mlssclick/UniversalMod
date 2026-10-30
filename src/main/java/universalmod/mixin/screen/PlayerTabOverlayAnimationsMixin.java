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
public abstract class PlayerTabOverlayAnimationsMixin {
    @Unique
    private boolean universalmod$pushed;

    @Inject(method = "setVisible", at = @At("HEAD"))
    private void universalmod$visible(boolean visible, CallbackInfo ci) {
        Animations a = Animations.getInstance();
        if (a != null) a.setTabVisible(visible);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void universalmod$head(DrawContext context, int width, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        universalmod$pushed = false;
        if (!Animations.active(Animations.TAB)) return;
        Animations a = Animations.getInstance();
        float p = a == null ? 1f : a.tabProgress();
        if (p >= .999f) return;
        context.getMatrices().push();
        universalmod$pushed = true;
        context.getMatrices().translate(0, -200f * (1f - p), 0);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void universalmod$tail(DrawContext context, int width, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        if (universalmod$pushed) {
            context.getMatrices().pop();
            universalmod$pushed = false;
        }
    }
}
