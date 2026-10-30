package universalmod.module.misc;

import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.ShulkerBoxBlock;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.WorldRenderEvent;
import universalmod.feature.cooldown.HolyWorldHealingCooldown;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.misc.serverhelper.EndHelper;
import universalmod.setting.ButtonSetting;
import universalmod.setting.NumberSetting;
import universalmod.setting.BindSetting;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.bind.KeyBind;

public final class ServerHelper extends Module {
    private static ServerHelper instance;

    private final BooleanSetting helperEnabled = register(new BooleanSetting("Enabled", "Enables HW helper logic.", true));
    private final BooleanSetting trapkaEnabled = register(new BooleanSetting("Trapka", "Shows trapka preview.", true));
    private final BooleanSetting explosionTrapEnabled = register(new BooleanSetting("Explosion Trap", "Shows explosion trap preview.", true));
    private final BooleanSetting stunEnabled = register(new BooleanSetting("Stun", "Shows stun preview and timer.", true));
    private final BooleanSetting stunMemoryEnabled = register(new BooleanSetting("Remember Stun Zone", "Remembers and displays stun zones.", true));
    private final BooleanSetting fillEnabled = register(new BooleanSetting("Fill", "Fills helper boxes.", false));
    private final BooleanSetting goldenSpawnersEnabled = register(new BooleanSetting("Golden Spawners", "Shows extended golden spawner NBT info.", true));
    private final BooleanSetting goldenSpawnersSlotFill = register(new BooleanSetting("Golden Spawners Slot Fill", "Fills slots with golden spawner info.", true));
    private final BooleanSetting compassCooldownsEnabled = register(new BooleanSetting("Compass Cooldowns", "Shows compass cooldowns in item names.", true));
    private final BooleanSetting compassCooldownsSlotFill = register(new BooleanSetting("Compass Cooldowns Slot Fill", "Fills slots for ready compass cooldowns.", true));
    private final BooleanSetting macrosExpanded = register(new BooleanSetting("Macros", "Shows Server Helper hotbar macro binds.", false));
    private final ColorSetting outlineColor = register(new ColorSetting("Outline Color", "Default helper outline color.", new Color(255, 255, 255, 255).getRGB()));
    private final ColorSetting insideColor = register(new ColorSetting("Inside Color", "Color when a visible player is inside the zone.", new Color(255, 59, 48, 255).getRGB()));
    private boolean endHelperExpanded;
    private final ButtonSetting endHelperButton = register(new ButtonSetting(
            "EndHelper", "Opens End Helper settings.", () -> endHelperExpanded = !endHelperExpanded));
    private final BooleanSetting endHelperEnabled = register(new BooleanSetting(
            "End Helper Enabled", "Enables the End trajectory and surface history.", false));
    private final BooleanSetting trajectory = register(new BooleanSetting(
            "Trajectory", "Draws the End trajectory guide.", true));
    private final NumberSetting trajectoryY = register(new NumberSetting(
            "Trajectory Y", "Target Y level for the trajectory guide.", 100.0, 20.0, 255.0, 1.0));
    private final BooleanSetting history = register(new BooleanSetting(
            "History", "Draws cached End-stone surface history.", true));
    private final ColorSetting historyColor = register(new ColorSetting(
            "History Color", "Color of cached End-stone history.", new Color(138, 92, 255, 255).getRGB()));
    private final NumberSetting historyOpacity = register(new NumberSetting(
            "History Opacity", "Opacity of cached End-stone history.", 35.0, 0.0, 100.0, 1.0));
    private final EndHelper endHelper = new EndHelper(trajectory, trajectoryY, history, historyColor, historyOpacity);
    private final EnumMap<MacroAction, BindSetting> macroBinds = new EnumMap<>(MacroAction.class);
    private final EnumMap<MacroAction, Boolean> macroKeyStates = new EnumMap<>(MacroAction.class);

    public ServerHelper() {
        super("holyworld_helper", "Server Helper", ModuleCategory.MISC);
        instance = this;
        Detection.init();
        trapkaEnabled.visibleWhen(helperEnabled::getValue);
        explosionTrapEnabled.visibleWhen(helperEnabled::getValue);
        stunEnabled.visibleWhen(helperEnabled::getValue);
        stunMemoryEnabled.visibleWhen(helperEnabled::getValue);
        fillEnabled.visibleWhen(helperEnabled::getValue);
        goldenSpawnersEnabled.visibleWhen(helperEnabled::getValue);
        goldenSpawnersSlotFill.visibleWhen(() -> helperEnabled.getValue() && goldenSpawnersEnabled.getValue());
        compassCooldownsEnabled.visibleWhen(helperEnabled::getValue);
        compassCooldownsSlotFill.visibleWhen(() -> helperEnabled.getValue() && compassCooldownsEnabled.getValue());
        macrosExpanded.visibleWhen(helperEnabled::getValue);
        outlineColor.visibleWhen(helperEnabled::getValue);
        insideColor.visibleWhen(helperEnabled::getValue);
        endHelperEnabled.visibleWhen(() -> endHelperExpanded);
        trajectory.visibleWhen(() -> endHelperExpanded && endHelperEnabled.getValue());
        trajectoryY.visibleWhen(() -> endHelperExpanded && endHelperEnabled.getValue() && trajectory.getValue());
        history.visibleWhen(() -> endHelperExpanded && endHelperEnabled.getValue());
        historyColor.visibleWhen(() -> endHelperExpanded && endHelperEnabled.getValue() && history.getValue());
        historyOpacity.visibleWhen(() -> endHelperExpanded && endHelperEnabled.getValue() && history.getValue());
        endHelperEnabled.addListener((setting, before, after) -> {
            if (Boolean.FALSE.equals(after)) endHelper.reset();
        });


        for (MacroAction action : MacroAction.values()) {
            BindSetting bind = register(new BindSetting(
                    action.bindSettingName(),
                    "Hotbar macro bind for " + action.translationKey() + ".",
                    KeyBind.NONE
            ));
            bind.visibleWhen(() -> helperEnabled.getValue() && macrosExpanded.getValue());
            macroBinds.put(action, bind);
            macroKeyStates.put(action, false);
        }
    }

    public static ServerHelper getInstance() {
        return instance;
    }

    public static boolean isTrackerEnabled() {
        ServerHelper helper = instance;
        return helper != null && helper.isEnabled() && helper.helperEnabled.getValue();
    }

    public static boolean isStunMemoryActive() {
        ServerHelper helper = instance;
        return helper != null && helper.isEnabled() && helper.helperEnabled.getValue() && helper.stunMemoryEnabled.getValue();
    }

    public boolean rendersTrapka() {
        return trapkaEnabled.getValue();
    }

    public boolean rendersExplosionTrap() {
        return explosionTrapEnabled.getValue();
    }

    public boolean rendersStun() {
        return stunEnabled.getValue();
    }

    public boolean remembersStun() {
        return stunMemoryEnabled.getValue();
    }

    public boolean fillsBoxes() {
        return fillEnabled.getValue();
    }

    public boolean showsGoldenSpawners() {
        return helperEnabled.getValue() && goldenSpawnersEnabled.getValue();
    }

    public boolean fillsGoldenSpawnerSlots() {
        return showsGoldenSpawners() && goldenSpawnersSlotFill.getValue();
    }

    public boolean showsCompassCooldowns() {
        return helperEnabled.getValue() && compassCooldownsEnabled.getValue();
    }

    public boolean fillsCompassCooldownSlots() {
        return showsCompassCooldowns() && compassCooldownsSlotFill.getValue();
    }

    public int outlineColor() {
        return outlineColor.getValue();
    }

    public int insideColor() {
        return insideColor.getValue();
    }

    public KeyBind getMacroBind(MacroAction action) {
        BindSetting setting = action == null ? null : macroBinds.get(action);
        return setting == null ? KeyBind.NONE : setting.getValue();
    }

    public void setMacroBind(MacroAction action, KeyBind bind) {
        BindSetting setting = action == null ? null : macroBinds.get(action);
        if (setting != null) {
            setting.setValue(bind == null ? KeyBind.NONE : bind);
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (endHelperEnabled.getValue()) {
            endHelper.onTick(client);
        } else {
            endHelper.reset();
        }
        if (client == null || client.player == null || client.getWindow() == null || client.currentScreen != null) {
            resetMacroKeyStates();
            return;
        }

        long window = client.getWindow().getHandle();
        for (MacroAction action : MacroAction.values()) {
            KeyBind bind = getMacroBind(action);
            boolean down = bind.isDown(window);
            boolean wasDown = macroKeyStates.getOrDefault(action, false);
            if (down && !wasDown) {
                switchHotbarTo(client, action);
            }
            macroKeyStates.put(action, down);
        }
    }

    private void switchHotbarTo(MinecraftClient client, MacroAction action) {
        if (client == null || client.player == null || action == null) {
            return;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (matchesMacro(stack, action)) {

                client.player.getInventory().selectedSlot = slot;
                return;
            }
        }
    }

    private boolean matchesMacro(ItemStack stack, MacroAction action) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return switch (action) {
            case STUN -> stack.getItem() == Items.NETHER_STAR;
            case EXPLOSION_TRAP -> stack.getItem() == Items.PRISMARINE_SHARD;
            case TRAPKA -> stack.getItem() == Items.POPPED_CHORUS_FRUIT;
            case SNOWBALL -> stack.getItem() == Items.SNOWBALL;
            case FIRE_CHARGE -> stack.getItem() == Items.FIRE_CHARGE;
            case HEALING_POTION -> stack.getItem() == Items.POTION && HolyWorldHealingCooldown.isHealingPotion(stack);
            case SPLASH_HEALING_POTION -> stack.getItem() == Items.SPLASH_POTION && HolyWorldHealingCooldown.isHealingPotion(stack);
            case BACKPACK -> stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
        };
    }

    private void resetMacroKeyStates() {
        for (MacroAction action : MacroAction.values()) {
            macroKeyStates.put(action, false);
        }
    }

    @Override
    protected void onDisable() {
        Detection.reset();
        resetMacroKeyStates();
        endHelper.reset();
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        universalmod.module.misc.serverhelper.ServerHelperRenderer.renderWorld(mc, event, this);
        if (endHelperEnabled.getValue()) {
            endHelper.render(mc);
        }
    }

    public enum MacroAction {
        STUN("Macro Stun", "Macro Bind Stun"),
        EXPLOSION_TRAP("Macro Explosion Trap", "Macro Bind Explosion Trap"),
        TRAPKA("Macro Trapka", "Macro Bind Trapka"),
        SNOWBALL("Macro Snowball", "Macro Bind Snowball"),
        FIRE_CHARGE("Macro Fire Charge", "Macro Bind Fire Charge"),
        HEALING_POTION("Macro Healing Potion", "Macro Bind Healing Potion"),
        SPLASH_HEALING_POTION("Macro Splash Healing Potion", "Macro Bind Splash Healing Potion"),
        BACKPACK("Macro Backpack", "Macro Bind Backpack");

        private final String translationKey;
        private final String bindSettingName;

        MacroAction(String translationKey, String bindSettingName) {
            this.translationKey = translationKey;
            this.bindSettingName = bindSettingName;
        }

        public String translationKey() {
            return translationKey;
        }

        public String bindSettingName() {
            return bindSettingName;
        }
    }


    public static boolean isReallyWorld() { return universalmod.module.misc.serverhelper.ServerInfoService.isReallyWorld(); }
    public static boolean isSingleplayer() { return universalmod.module.misc.serverhelper.ServerInfoService.isSingleplayer(); }
    public static String serverAddress() { return universalmod.module.misc.serverhelper.ServerInfoService.serverAddress(); }
    public static String serverBrand() { return universalmod.module.misc.serverhelper.ServerInfoService.serverBrand(); }
    public static String pingText(PlayerListEntry playerInfo) { return universalmod.module.misc.serverhelper.ServerInfoService.pingText(playerInfo); }
    public static int ping(PlayerListEntry playerInfo) { return universalmod.module.misc.serverhelper.ServerInfoService.ping(playerInfo); }

    public static final class Detection {
        private Detection() {}
        public static void init() { universalmod.module.misc.serverhelper.TrapDetectionService.init(); }
        public static void reset() { universalmod.module.misc.serverhelper.TrapDetectionService.reset(); }
        public static void recordFinishedUse(PlayerEntity player) { universalmod.module.misc.serverhelper.TrapDetectionService.recordFinishedUse(player); }
        public static void handleSoundPacket(PlaySoundS2CPacket packet) { universalmod.module.misc.serverhelper.TrapDetectionService.handleSoundPacket(packet); }
    }

    public static final class ItemInfo {
        private ItemInfo() {}
        public static void appendTooltip(ItemStack stack, List<Text> lines) { universalmod.module.misc.serverhelper.ServerItemMetadataService.appendTooltip(stack, lines); }
        public static Text appendCompassCooldownToName(ItemStack stack, Text original) { return universalmod.module.misc.serverhelper.ServerItemMetadataService.appendCompassCooldownToName(stack, original); }
        public static boolean isCompassCooldownOverlayStack(ItemStack stack) { return universalmod.module.misc.serverhelper.ServerItemMetadataService.isCompassCooldownOverlayStack(stack); }
        public static int getSlotFillColor(ItemStack stack) { return universalmod.module.misc.serverhelper.ServerItemMetadataService.getSlotFillColor(stack); }
    }


}
