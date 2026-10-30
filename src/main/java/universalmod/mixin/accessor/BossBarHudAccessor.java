package universalmod.mixin.accessor;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.UUID;

@Mixin(BossBarHud.class)
public interface BossBarHudAccessor {
    @Accessor("bossBars")
    Map<UUID, ClientBossBar> universalmod$getBossBars();

    @Accessor("BACKGROUND_TEXTURES")
    static Identifier[] universalmod$getBackgroundTextures() {
        throw new AssertionError();
    }

    @Accessor("PROGRESS_TEXTURES")
    static Identifier[] universalmod$getProgressTextures() {
        throw new AssertionError();
    }

    @Accessor("NOTCHED_BACKGROUND_TEXTURES")
    static Identifier[] universalmod$getNotchedBackgroundTextures() {
        throw new AssertionError();
    }

    @Accessor("NOTCHED_PROGRESS_TEXTURES")
    static Identifier[] universalmod$getNotchedProgressTextures() {
        throw new AssertionError();
    }

    @Invoker("renderBossBar")
    void universalmod$renderBossBar(DrawContext context, int x, int y, BossBar bossBar);
}
