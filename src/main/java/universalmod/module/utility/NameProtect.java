package universalmod.module.utility;

import net.minecraft.client.Minecraft;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.TextFactoryEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.StringSetting;

public final class NameProtect extends Module {
    private static NameProtect instance;
    private final StringSetting nameSetting = register(new StringSetting("Name", "Replacement nickname", "Protected", 32));

    public NameProtect() {
        super("nick_hider", "Name Protect", ModuleCategory.UTILS);
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static String replace(String text) {
        if (!isActive() || text == null || text.isEmpty()) {
            return text;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getUser() == null) {
            return text;
        }
        String username = client.getUser().getName();
        if (username == null || username.isEmpty()) {
            return text;
        }
        String replacement = instance.nameSetting.getValue();
        if (replacement == null) {
            replacement = "";
        }
        return text.replace(username, replacement);
    }

    @SubscribeEvent
    private void onTextFactory(TextFactoryEvent event) {
        if (isEnabled()) {
            event.setText(replace(event.getText()));
        }
    }
}
