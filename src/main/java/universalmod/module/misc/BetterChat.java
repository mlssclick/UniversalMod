package universalmod.module.misc;

import java.time.LocalTime;
import java.util.Locale;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import universalmod.service.language.LanguageManager;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;

@ModuleInformation(
        moduleName = "BetterChat",
        moduleCategory = ModuleCategory.MISC
)
public final class BetterChat extends Module {
    private static final long SMOOTH_CHAT_DURATION_MILLIS = 180L;
    private static final float SMOOTH_CHAT_OFFSET = 8.0f;

    private static BetterChat instance;

    private final BooleanSetting timestamps = new BooleanSetting("Timestamps", true);
    private final BooleanSetting antiSpam = new BooleanSetting("Anti Spam", true);
    private final BooleanSetting showSeconds = new BooleanSetting("Show Seconds", false);
    private final BooleanSetting twentyFourHourClock = new BooleanSetting("24 Hour Clock", true);
    private final BooleanSetting smoothChat = new BooleanSetting("Smooth Chat", true);
    private final BooleanSetting antiClear = new BooleanSetting("Anti Clear", false);

    private long lastMessageTimestampMillis;
    private String lastRawMessageKey = "";
    private int lastRawMessageCount;

    public BetterChat() {
        instance = this;
        timestamps.setDescription("Adds clean timestamps to incoming chat.");
        antiSpam.setDescription("Merges repeated messages into one counted message.");
        showSeconds.setDescription("Shows seconds in timestamps.");
        twentyFourHourClock.setDescription("Uses 24 hour timestamp format.");
        smoothChat.setDescription("Slides new chat messages smoothly.");
        antiClear.setDescription("Prevents servers from clearing chat.");
    }

    public static ProcessedChatMessage prepareIncomingMessage(Text message) {
        BetterChat module = instance;
        if (module == null || !module.isEnabled()) {
            return new ProcessedChatMessage(message, false, false);
        }

        module.lastMessageTimestampMillis = System.currentTimeMillis();
        if (message == null) {
            return new ProcessedChatMessage(null, false, false);
        }

        String rawKey = message.getString();
        boolean replacePrevious = false;
        int duplicateCount = 1;

        if (module.antiSpam.getValue() && !rawKey.isBlank() && rawKey.equals(module.lastRawMessageKey)) {
            module.lastRawMessageCount++;
            duplicateCount = module.lastRawMessageCount;
            replacePrevious = duplicateCount > 1;
        } else {
            module.lastRawMessageKey = rawKey;
            module.lastRawMessageCount = 1;
        }

        return new ProcessedChatMessage(module.formatMessage(message, duplicateCount), replacePrevious, true);
    }

    public static boolean shouldCancelChatClear() {
        BetterChat module = instance;
        return module != null && module.isEnabled() && module.antiClear.getValue();
    }

    public static void resetSpamState() {
        BetterChat module = instance;
        if (module == null) {
            return;
        }
        module.lastRawMessageKey = "";
        module.lastRawMessageCount = 0;
    }

    public static float getSmoothOffset() {
        BetterChat module = instance;
        if (module == null || !module.isEnabled() || !module.smoothChat.getValue()) {
            return 0.0f;
        }

        long elapsed = System.currentTimeMillis() - module.lastMessageTimestampMillis;
        if (elapsed <= 0L || elapsed >= SMOOTH_CHAT_DURATION_MILLIS) {
            return 0.0f;
        }

        float progress = (float) elapsed / (float) SMOOTH_CHAT_DURATION_MILLIS;
        float eased = 1.0f - progress;
        eased = eased * eased * (3.0f - 2.0f * eased);
        return SMOOTH_CHAT_OFFSET * eased;
    }

    private Text formatMessage(Text message, int duplicateCount) {
        MutableText content = message.copy();
        if (duplicateCount > 1) {
            content.append(Text.literal(LanguageManager.translateFormat("chat.duplicate", duplicateCount))
                    .setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
        }

        if (!timestamps.getValue()) {
            return content;
        }

        MutableText prefix = Text.literal(buildTimestampPrefix())
                .setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY));
        return Text.empty().append(prefix).append(content);
    }

    private String buildTimestampPrefix() {
        LocalTime now = LocalTime.now();
        boolean withSeconds = showSeconds.getValue();

        if (twentyFourHourClock.getValue()) {
            return withSeconds
                    ? String.format(Locale.US, "[%02d:%02d:%02d] ", now.getHour(), now.getMinute(), now.getSecond())
                    : String.format(Locale.US, "[%02d:%02d] ", now.getHour(), now.getMinute());
        }

        int hour = now.getHour() % 12;
        if (hour == 0) {
            hour = 12;
        }
        String meridiem = now.getHour() >= 12 ? "PM" : "AM";
        return withSeconds
                ? String.format(Locale.US, "[%d:%02d:%02d %s] ", hour, now.getMinute(), now.getSecond(), meridiem)
                : String.format(Locale.US, "[%d:%02d %s] ", hour, now.getMinute(), meridiem);
    }

    public record ProcessedChatMessage(Text text, boolean replacePrevious, boolean moduleActive) {
    }
}
