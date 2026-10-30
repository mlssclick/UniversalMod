package universalmod.module.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;

public final class AutoSprint extends Module {
    public AutoSprint() {
        super("auto_sprint", "Auto Sprint", ModuleCategory.UTILS);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null || client.options == null) {
            return;
        }
        client.options.keySprint.setDown(false);
        boolean forward = client.options.keyUp.isDown();
        boolean canSprint = forward
                && !client.player.isInWater()
                && !client.player.isFallFlying()
                && !client.player.isUsingItem()
                && !client.player.hasEffect(MobEffects.BLINDNESS)
                && !client.player.isShiftKeyDown()
                && !client.player.horizontalCollision;
        client.player.setSprinting(canSprint);
    }
}
