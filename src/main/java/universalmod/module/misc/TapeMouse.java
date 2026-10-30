package universalmod.module.misc;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;

public final class TapeMouse extends Module {
    private static final String BUTTON_LMB = "LMB";
    private static final String BUTTON_RMB = "RMB";

    private final ModeSetting button = register(new ModeSetting(
            "Button",
            "Mouse button to auto click.",
            BUTTON_LMB,
            BUTTON_LMB,
            BUTTON_RMB
    ));
    private final NumberSetting delay = register(new NumberSetting(
            "Delay",
            "Delay between clicks in ticks.",
            20.0,
            1.0,
            100.0,
            1.0
    ));

    private int ticksUntilNextAction;

    public TapeMouse() {
        super("mouseclicker", "Tape Mouse", ModuleCategory.MISC);
    }

    @Override
    protected void onEnable() {
        resetTimer();
    }

    @Override
    protected void onDisable() {
        resetTimer();
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null
                || client.player == null
                || client.level == null
                || client.options == null
                || client.screen != null) {
            return;
        }

        if (ticksUntilNextAction > 0) {
            ticksUntilNextAction--;
            return;
        }

        KeyMapping mapping = button.is(BUTTON_LMB) ? client.options.keyAttack : client.options.keyUse;
        KeyMapping.click(mapping.getDefaultKey());
        ticksUntilNextAction = getDelayTicks();
    }

    private void resetTimer() {
        ticksUntilNextAction = getDelayTicks();
    }

    private int getDelayTicks() {
        return Math.max(1, (int) Math.round(delay.getValue()));
    }
}
