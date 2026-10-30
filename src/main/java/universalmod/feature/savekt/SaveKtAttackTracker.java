package universalmod.feature.savekt;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;


public final class SaveKtAttackTracker {
    private static boolean initialized;

    private SaveKtAttackTracker() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            SaveKtLeaveService.getInstance().onAttack();
            return InteractionResult.PASS;
        });
    }
}
