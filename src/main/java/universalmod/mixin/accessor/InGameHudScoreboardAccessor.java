package universalmod.mixin.accessor;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Comparator;

@Mixin(InGameHud.class)
public interface InGameHudScoreboardAccessor {
    @Accessor("SCOREBOARD_ENTRY_COMPARATOR")
    static Comparator<ScoreboardEntry> universalmod$getScoreboardEntryComparator() {
        throw new AssertionError();
    }

    @Accessor("SCOREBOARD_JOINER")
    static String universalmod$getScoreboardJoiner() {
        throw new AssertionError();
    }
}
