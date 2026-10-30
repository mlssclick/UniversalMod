package universalmod.mixin.screen;

import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.Style;
import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import universalmod.event.type.TextFactoryEvent;
import universalmod.lifecycle.ClientRuntime;

@Mixin(TextVisitFactory.class)
public abstract class TextVisitFactoryMixin {
    @ModifyVariable(method = "visitForwards(Ljava/lang/String;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static String universalmod$nameProtectForward(String text) {
        return universalmod$protect(text);
    }

    @ModifyVariable(method = "visitBackwards(Ljava/lang/String;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static String universalmod$nameProtectBackward(String text) {
        return universalmod$protect(text);
    }

    @ModifyVariable(method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static String universalmod$nameProtectFormatted(String text) {
        return universalmod$protect(text);
    }

    private static String universalmod$protect(String text) {
        TextFactoryEvent event = ClientRuntime.postEvent(new TextFactoryEvent(text));
        return event.getText();
    }
}
