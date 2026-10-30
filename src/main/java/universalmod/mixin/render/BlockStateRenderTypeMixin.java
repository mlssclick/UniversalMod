package universalmod.mixin.render;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.render.NoRender;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockStateRenderTypeMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void universalmod$plants(CallbackInfoReturnable<BlockRenderType> cir) {
        NoRender noRender = NoRender.getInstance();
        BlockState state = (BlockState) (Object) this;
        if (noRender != null && noRender.shouldHidePlants() && noRender.isPlantState(state)) {
            cir.setReturnValue(BlockRenderType.INVISIBLE);
        }
    }
}
