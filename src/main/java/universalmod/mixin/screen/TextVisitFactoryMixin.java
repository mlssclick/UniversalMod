package universalmod.mixin.screen;

import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import universalmod.event.type.TextFactoryEvent;
import universalmod.lifecycle.Manager;

@Mixin(StringDecomposer.class)
public class TextVisitFactoryMixin {
    @ModifyVariable(method = "iterate(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static String universalmod$nameProtectForward(String text) {
        return universalmod$protect(text);
    }

    @ModifyVariable(method = "iterateBackwards(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static String universalmod$nameProtectBackward(String text) {
        return universalmod$protect(text);
    }

    @ModifyVariable(method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static String universalmod$nameProtectFormatted(String text) {
        return universalmod$protect(text);
    }

    private static String universalmod$protect(String text) {
        TextFactoryEvent event = Manager.postEvent(new TextFactoryEvent(text));
        return event.getText();
    }
}
