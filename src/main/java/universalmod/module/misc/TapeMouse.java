package universalmod.module.misc;

import net.minecraft.client.option.KeyBinding;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;

@ModuleInformation(
        moduleName = "Tape Mouse",
        moduleCategory = ModuleCategory.MISC
)
public final class TapeMouse extends Module {
    private static final String BUTTON_LMB = "LMB";
    private static final String BUTTON_RMB = "RMB";

    private final ModeSetting button = new ModeSetting("Button", BUTTON_LMB, BUTTON_LMB, BUTTON_RMB);
    private final NumberSetting delay = new NumberSetting("Delay", 20.0, 1.0, 100.0, 1.0);

    public TapeMouse() {
        button.setDescription("Mouse button to auto click.");
        delay.setDescription("Delay between clicks in ticks.");
    }

    private int ticksUntilNextAction;

    @Override
    public void onEnable() {
        super.onEnable();
        resetTimer();
    }

    @Override
    public void onDisable() {
        resetTimer();
        super.onDisable();
    }

    @Override
    public void onTick(net.minecraft.client.MinecraftClient tickClient) {
        if (mc.player == null || mc.world == null || mc.options == null || mc.currentScreen != null) {
            return;
        }

        if (ticksUntilNextAction > 0) {
            ticksUntilNextAction--;
            return;
        }

        KeyBinding mapping = button.is(BUTTON_LMB) ? mc.options.attackKey : mc.options.useKey;
        KeyBinding.onKeyPressed(mapping.getDefaultKey());
        ticksUntilNextAction = getDelayTicks();
    }

    private void resetTimer() {
        ticksUntilNextAction = getDelayTicks();
    }

    private int getDelayTicks() {
        return Math.max(1, (int) Math.round(delay.getValue()));
    }
}
