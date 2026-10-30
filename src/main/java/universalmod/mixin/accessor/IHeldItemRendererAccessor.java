package universalmod.mixin.accessor;

import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({HeldItemRenderer.class})
public interface IHeldItemRendererAccessor {
    @Accessor("equipProgressMainHand")
    float getEquipProgressMainHand();

    @Accessor("prevEquipProgressMainHand")
    float getPrevEquipProgressMainHand();
}
