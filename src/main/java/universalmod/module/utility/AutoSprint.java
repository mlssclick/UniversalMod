package universalmod.module.utility;

import net.minecraft.entity.effect.StatusEffects;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;

@ModuleInformation(
        moduleName = "Auto Sprint",
        moduleCategory = ModuleCategory.UTILS
)
public final class AutoSprint extends Module {
    @Override
    public void onTick(net.minecraft.client.MinecraftClient tickClient) {
        if (mc.player == null || mc.options == null) {
            return;
        }

        mc.options.sprintKey.setPressed(false);

        boolean forward = mc.options.forwardKey.isPressed();
        boolean canSprint = forward
                && !mc.player.isTouchingWater()
                && !mc.player.isFallFlying()
                && !mc.player.isUsingItem()
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !mc.player.isSneaking()
                && !mc.player.horizontalCollision;

        mc.player.setSprinting(canSprint);
    }
}
