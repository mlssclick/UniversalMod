package universalmod.mixin.render;

import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import universalmod.access.FireGlowEntityStateCarrier;

@Mixin(EntityRenderState.class)
public abstract class FireGlowEntityRenderStateMixin implements FireGlowEntityStateCarrier {
    @Unique private int universalmod$fireOutlineColor;
    @Override public int universalmod$getFireOutlineColor() { return universalmod$fireOutlineColor; }
    @Override public void universalmod$setFireOutlineColor(int color) { universalmod$fireOutlineColor=color; }
}
