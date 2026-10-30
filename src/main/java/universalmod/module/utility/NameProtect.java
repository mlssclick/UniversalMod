package universalmod.module.utility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.TextFactoryEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.StringSetting;

@ModuleInformation(
        moduleName = "Name Protect",
        moduleCategory = ModuleCategory.UTILS
)
public final class NameProtect extends Module {
    private static NameProtect instance;
    public final StringSetting name = new StringSetting("Name", "Protected", 32);

    public NameProtect() {
        name.setDescription("Replacement nickname");
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static String replace(String text) {
        if (!isActive() || text == null || text.isEmpty()) {
            return text;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSession() == null) {
            return text;
        }
        String username = client.getSession().getUsername();
        if (username == null || username.isEmpty()) {
            return text;
        }
        String replacement = instance.name.getValue();
        if (replacement == null) {
            replacement = "";
        }
        return text.replace(username, replacement);
    }

    public static Text replace(Text text) {
        if (!isActive() || text == null) {
            return text;
        }
        String original = text.getString();
        String replaced = replace(original);
        return replaced.equals(original) ? text : Text.literal(replaced).setStyle(text.getStyle());
    }

    @SubscribeEvent
    private void onTextFactory(TextFactoryEvent event) {
        if (isEnabled()) {
            event.setText(replace(event.getText()));
        }
    }

    public String replaceText(String text) {
        return replace(text);
    }
}
