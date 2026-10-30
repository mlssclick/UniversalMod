package universalmod.mixin.accessor;

import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ButtonWidget.class)
public interface ButtonWidgetAccessor {
    @Mutable
    @Accessor("onPress")
    void universalmod$setOnPress(ButtonWidget.PressAction action);
}
