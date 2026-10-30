package universalmod.mixin.render;

import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Ambience;

@Mixin(ClientWorld.class)
public abstract class ClientWorldAmbienceMixin {
    @Inject(method = "tickTime", at = @At("TAIL"), require = 0)
    private void universalmod$overrideTimeAndWeather(CallbackInfo ci) {
        universalmod$applyLocalAmbienceTimeAndWeather();
    }

    @Inject(method = "setTime", at = @At("TAIL"), require = 0)
    private void universalmod$stabilizeLocalAmbienceTime(long time, long timeOfDay, boolean shouldTickTimeOfDay, CallbackInfo ci) {
        universalmod$applyLocalAmbienceTime();
    }

    private void universalmod$applyLocalAmbienceTimeAndWeather() {
        Ambience ambience = Ambience.getInstance();
        if (ambience == null || !ambience.isEnabled()) {
            return;
        }

        ClientWorld world = (ClientWorld) (Object) this;
        ClientWorld.Properties properties = world.getLevelProperties();
        properties.setTimeOfDay(ambience.getInternalTime());
        ambience.syncWeather(world, properties);
    }

    private void universalmod$applyLocalAmbienceTime() {
        Ambience ambience = Ambience.getInstance();
        if (ambience == null || !ambience.isEnabled()) {
            return;
        }

        ClientWorld world = (ClientWorld) (Object) this;
        world.getLevelProperties().setTimeOfDay(ambience.getInternalTime());
    }
}
