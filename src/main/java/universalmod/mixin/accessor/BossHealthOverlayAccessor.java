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
public interface BossHealthOverlayAccessor {

    @Accessor("BACKGROUND_TEXTURES")
    static Identifier[] universalmod$getBarBackgroundSprites() {
        throw new AssertionError();
    }

    @Accessor("PROGRESS_TEXTURES")
    static Identifier[] universalmod$getBarProgressSprites() {
        throw new AssertionError();
    }

    @Accessor("NOTCHED_BACKGROUND_TEXTURES")
    static Identifier[] universalmod$getOverlayBackgroundSprites() {
        throw new AssertionError();
    }

    @Accessor("NOTCHED_PROGRESS_TEXTURES")
    static Identifier[] universalmod$getOverlayProgressSprites() {
        throw new AssertionError();
    }
    @Accessor("bossBars")
    Map<UUID, ClientBossBar> universalmod$getEvents();

    @Invoker("renderBossBar")
    void universalmod$drawBar(DrawContext graphics, int x, int y, BossBar event);
}
