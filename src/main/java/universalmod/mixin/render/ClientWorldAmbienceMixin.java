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
        Ambience ambience = Ambience.getInstance();
        if (ambience == null || !ambience.isEnabled()) return;
        ClientWorld world = (ClientWorld) (Object) this;
        ClientWorld.Properties properties = world.getLevelProperties();
        properties.setTimeOfDay(ambience.getInternalTime());
        ambience.syncWeather(world, properties);
    }

    
    
    @Inject(method = "setTimeOfDay", at = @At("TAIL"), require = 0)
    private void universalmod$stabilizeLocalTime(long timeOfDay, CallbackInfo ci) {
        Ambience ambience = Ambience.getInstance();
        if (ambience == null || !ambience.isEnabled()) return;
        ((ClientWorld) (Object) this).getLevelProperties().setTimeOfDay(ambience.getInternalTime());
    }
}
