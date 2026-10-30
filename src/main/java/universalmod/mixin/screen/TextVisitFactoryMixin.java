package universalmod.mixin.screen;

import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import universalmod.event.type.TextFactoryEvent;
import universalmod.lifecycle.Manager;

@Mixin(TextVisitFactory.class)
public class TextVisitFactoryMixin {
    @ModifyVariable(method = "visitForwards(Ljava/lang/String;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static String universalmod$nameProtectForward(String text) {
        return universalmod$protect(text);
    }

    @ModifyVariable(method = "visitBackwards(Ljava/lang/String;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static String universalmod$nameProtectBackward(String text) {
        return universalmod$protect(text);
    }

    @ModifyVariable(method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static String universalmod$nameProtectFormatted(String text) {
        return universalmod$protect(text);
    }

    private static String universalmod$protect(String text) {
        TextFactoryEvent event = Manager.postEvent(new TextFactoryEvent(text));
        return event.getText();
    }
}
