package universalmod.mixin.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.NoRender;
import universalmod.module.render.Scoreboard;

@Mixin(InGameHud.class)
public abstract class InGameHudScoreboardMixin {
    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$replaceScoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.isActive("Scoreboard") || Scoreboard.isActive()) ci.cancel();
    }
}
