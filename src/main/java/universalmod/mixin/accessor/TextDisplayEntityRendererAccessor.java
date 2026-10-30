package universalmod.mixin.accessor;

import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntityRenderer.TextDisplayEntityRenderer.class)
public interface TextDisplayEntityRendererAccessor {
    @Invoker("getLines")
    DisplayEntity.TextDisplayEntity.TextLines universalmod$invokeGetLines(Text text, int width);
}
