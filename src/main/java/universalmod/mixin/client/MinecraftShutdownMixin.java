package universalmod.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.Engine;

@Mixin(MinecraftClient.class)
public abstract class MinecraftShutdownMixin {
    private static volatile boolean universalmod$shutdownStarted;

    @Inject(method = "stop()V", at = @At("HEAD"), require = 0)
    private void universalmod$shutdownClient(CallbackInfo info) {
        if (universalmod$shutdownStarted) {
            return;
        }
        universalmod$shutdownStarted = true;
        Engine.shutdown();
    }
}
