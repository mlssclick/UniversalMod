package universalmod.mixin.screen;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import universalmod.module.utility.CustomDonate;








@Mixin(InGameHud.class)
public abstract class InGameHudCustomDonateMixin {
    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/font/TextRenderer;getWidth(Lnet/minecraft/text/StringVisitable;)I",
                    ordinal = 1
            ),
            index = 0,
            require = 0
    )
    private StringVisitable universalmod$replaceDonateScoreboardWidth(StringVisitable text) {
        if (text instanceof Text component) {
            return CustomDonate.replaceScoreboardLine(component);
        }
        return text;
    }

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I",
                    ordinal = 1
            ),
            index = 1,
            require = 0
    )
    private Text universalmod$replaceDonateScoreboardDraw(Text text) {
        return CustomDonate.replaceScoreboardLine(text);
    }
}
