package universalmod.mixin.accessor;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Comparator;

@Mixin(InGameHud.class)
public interface GuiScoreboardAccessor {
    @Accessor("SCOREBOARD_ENTRY_COMPARATOR")
    static Comparator<ScoreboardEntry> universalmod$getScoreDisplayOrder() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Accessor("SCOREBOARD_JOINER")
    static String universalmod$getScoreboardSpacer() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
