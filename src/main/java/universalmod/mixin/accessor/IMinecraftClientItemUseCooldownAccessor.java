package universalmod.mixin.accessor;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({MinecraftClient.class})
public interface IMinecraftClientItemUseCooldownAccessor {
    @Accessor("itemUseCooldown")
    void setItemUseCooldown(int cooldown);
}
