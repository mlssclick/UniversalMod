package universalmod.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import universalmod.lifecycle.Manager;
import universalmod.module.utility.NameProtect;
import universalmod.feature.inventory.ReplaceUtil;

@Mixin(Team.class)
public class TeamMixin {
    @ModifyArg(
            method = "decorateName(Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/text/MutableText;append(Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;",
                    ordinal = 0
            ),
            index = 0
    )
    private Text universalmod$protectTeamName(Text original) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return original;
        }

        NameProtect module = Manager.getInstance().getModuleManager().get(NameProtect.class);
        if (module == null || !module.isEnabled()) {
            return original;
        }

        String username = client.getSession().getUsername();
        String replacement = module.replaceText(username);
        return username.equals(replacement) ? original : ReplaceUtil.replaceLiteral(original, username, replacement);
    }
}
