package universalmod.mixin.screen;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.event.type.TabCompleteEvent;
import universalmod.lifecycle.ClientRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
    @Shadow
    @Final
    TextFieldWidget textField;
    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true, require = 1)
    private void universalmod$localCommandSuggestions(CallbackInfo ci) {
        String input = textField.getText();
        TabCompleteEvent event = ClientRuntime.postEvent(new TabCompleteEvent(input));
        String[] completions = event.getCompletions();
        if (!event.isCancelled() || completions == null) return;
        int cursor = Math.max(0, Math.min(textField.getCursor(), input.length()));
        int start = input.lastIndexOf(' ', Math.max(0, cursor - 1)) + 1;
        StringRange range = StringRange.between(start, cursor);
        List<Suggestion> suggestions = new ArrayList<>(completions.length);
        for (String completion : completions) {
            if (completion != null && !completion.isBlank()) suggestions.add(new Suggestion(range, completion));
        }
        pendingSuggestions = CompletableFuture.completedFuture(new Suggestions(range, suggestions));
        ((ChatInputSuggestor) (Object) this).show(true);
        ci.cancel();
    }
}
