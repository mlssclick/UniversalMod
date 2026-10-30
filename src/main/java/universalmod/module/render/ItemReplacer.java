package universalmod.module.render;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.ButtonSetting;
import universalmod.setting.ModeSetting;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemReplacer extends Module {
    public static final int CUSTOM_MODEL_DATA_BASE = 1_500_000;
    private static final String NBT_BACKUP_PRESENT = "UniversalModItemReplacerHadCustomModelData";
    private static final String NBT_BACKUP_VALUE = "UniversalModItemReplacerOriginalCustomModelData";
    public static final String[] MODELS = {
            "abominableblade", "abominablegreatsaber", "abominablescythe", "aciddemon", "amethyst_shuriken", "ancient_royal_great_sword", "aquantic_sacred_blade", "aquantictrident", "arcanethyst", "ashura_blade", "bloodedge", "bloodydeath", "bramblethorn", "cariansword", "chrono_blade", "corruptedmythicblade", "creationsplitter", "crescentrose", "cyberkatana", "cybermantisblade", "cybernetickatana", "cyberneticknife", "cyberneticsawblade", "cybersword", "dainsleif", "dark_blade", "dark_cleaver", "death_knight_dagger", "death_knight_sword", "demigodsunholyblade", "demigodsunholyhalberd", "demonicblade", "demoniccleaver", "demonlordsgreataxe", "demonlordsword", "divine_justice", "divine_reaper", "divineaxerhitta", "divinepunisher", "dragonslayingblade", "edgeoftheastralplane", "emberblade", "enigma", "epicsword", "estoc", "excalibur", "fallengodspear", "fallengodsword", "floral_longsword", "floral_sabre", "forest_guardian_glaive", "frostaxe", "frostblade", "frostscythe", "greenscythe", "hearthflame", "herosword", "holymoonlightsword", "hornetsneedle", "icewhisper", "jadehalberd", "katana", "legendarysword", "longsword", "magiscythe", "masamune", "mjolnir", "moltenblade", "moltensword", "muramasa", "mysticalspellblade", "mythicblade", "partisan", "pharaohs_treasure", "pheonixgrace", "powerfusehammer", "powerfusesword", "requiem_of_hell", "ribboncleaver", "righteous_relic", "riversofblood", "royalchakram", "royalrapier", "sabre", "scissorblade", "sculkcleaver", "sculkscythe", "sculksword", "sentinels_will", "silverine_blade", "soul_collector", "soul_devourer", "soulclaws", "souledge", "soulharvester", "soulrender", "soulstealer", "steelsword", "stop_sign", "stormbringer", "storms_edge", "sunbreak", "tengensblade", "terrablade", "thousanddemondaggers", "thunderbrand", "thunderbringer", "vampiricneedle", "wakizashi", "watcher_claymore", "watching_warglaive", "waxweaver", "whisperwind", "wickpiercer", "yoru"
    };
    private static ItemReplacer instance;
    private final ModeSetting model = register(new ModeSetting("Item Replacer Model", MODELS[0], MODELS));
    private Runnable editorOpener = () -> {};
    private final ButtonSetting modelGallery = register(new ButtonSetting("Model", "Opens the item model gallery.", this::openGallery));
    private final Map<String, ItemStack> previews = new LinkedHashMap<>();

    public ItemReplacer() {
        super("Item Replacer", ModuleCategory.MISC);
        instance = this;
        model.setVisible(() -> false);
        for (int i = 0; i < MODELS.length; i++) {
            ItemStack preview = Items.NETHERITE_SWORD.getDefaultStack().copy();
            preview.getOrCreateNbt().putInt("CustomModelData", customModelDataForIndex(i));
            previews.put(MODELS[i], preview);
        }
    }

    public static ItemReplacer getInstance() {
        return instance;
    }

    public String getSelectedModel() {
        return model.getValue();
    }

    public void setSelectedModel(String value) {
        model.setValue(value);
    }

    public ItemStack getPreviewStack(String value) {
        ItemStack stack = previews.get(value);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    public void setEditorOpener(Runnable opener) {
        editorOpener = opener == null ? () -> {} : opener;
    }

    private void openGallery() {
        editorOpener.run();
    }

    public Identifier getSelectedModelId() {
        return new Identifier("mre", "item_replacer/sword/" + model.getValue());
    }

    public static int customModelDataForIndex(int index) {
        return CUSTOM_MODEL_DATA_BASE + Math.max(0, index) + 1;
    }

    public int selectedCustomModelData() {
        int index = model.getIndex();
        return customModelDataForIndex(index < 0 ? 0 : index);
    }

    public ItemStack apply(ItemStack original) {
        if (original == null || original.isEmpty() || !original.isIn(ItemTags.SWORDS)) {
            return original;
        }
        if (!isEnabled()) {
            return restoreOriginalModelData(original);
        }
        ItemStack replacement = original.copy();
        var nbt = replacement.getOrCreateNbt();
        if (!nbt.contains(NBT_BACKUP_PRESENT)) {
            boolean hadCustomModelData = nbt.contains("CustomModelData");
            nbt.putBoolean(NBT_BACKUP_PRESENT, hadCustomModelData);
            if (hadCustomModelData) {
                nbt.putInt(NBT_BACKUP_VALUE, nbt.getInt("CustomModelData"));
            }
        }
        nbt.putInt("CustomModelData", selectedCustomModelData());
        return replacement;
    }

    private ItemStack restoreOriginalModelData(ItemStack stack) {
        var nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_BACKUP_PRESENT)) {
            return stack;
        }
        ItemStack restored = stack.copy();
        var restoredNbt = restored.getOrCreateNbt();
        if (restoredNbt.getBoolean(NBT_BACKUP_PRESENT) && restoredNbt.contains(NBT_BACKUP_VALUE)) {
            restoredNbt.putInt("CustomModelData", restoredNbt.getInt(NBT_BACKUP_VALUE));
        } else {
            restoredNbt.remove("CustomModelData");
        }
        restoredNbt.remove(NBT_BACKUP_PRESENT);
        restoredNbt.remove(NBT_BACKUP_VALUE);
        return restored;
    }

    public static ItemStack applyStatic(ItemStack original) {
        ItemReplacer replacer = instance;
        return replacer == null ? original : replacer.apply(original);
    }
}
