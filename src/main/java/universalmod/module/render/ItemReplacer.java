package universalmod.module.render;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.ButtonSetting;
import universalmod.setting.ModeSetting;

@ModuleInformation(
        moduleName = "Item Replacer",
        moduleCategory = ModuleCategory.MISC
)
public final class ItemReplacer extends Module {
    public static final String[] MODELS = {
            "abominableblade",
            "abominablegreatsaber",
            "abominablescythe",
            "aciddemon",
            "amethyst_shuriken",
            "ancient_royal_great_sword",
            "aquantic_sacred_blade",
            "aquantictrident",
            "arcanethyst",
            "ashura_blade",
            "bloodedge",
            "bloodydeath",
            "bramblethorn",
            "cariansword",
            "chrono_blade",
            "corruptedmythicblade",
            "creationsplitter",
            "crescentrose",
            "cyberkatana",
            "cybermantisblade",
            "cybernetickatana",
            "cyberneticknife",
            "cyberneticsawblade",
            "cybersword",
            "dainsleif",
            "dark_blade",
            "dark_cleaver",
            "death_knight_dagger",
            "death_knight_sword",
            "demigodsunholyblade",
            "demigodsunholyhalberd",
            "demonicblade",
            "demoniccleaver",
            "demonlordsgreataxe",
            "demonlordsword",
            "divine_justice",
            "divine_reaper",
            "divineaxerhitta",
            "divinepunisher",
            "dragonslayingblade",
            "edgeoftheastralplane",
            "emberblade",
            "enigma",
            "epicsword",
            "estoc",
            "excalibur",
            "fallengodspear",
            "fallengodsword",
            "floral_longsword",
            "floral_sabre",
            "forest_guardian_glaive",
            "frostaxe",
            "frostblade",
            "frostscythe",
            "greenscythe",
            "hearthflame",
            "herosword",
            "holymoonlightsword",
            "hornetsneedle",
            "icewhisper",
            "jadehalberd",
            "katana",
            "legendarysword",
            "longsword",
            "magiscythe",
            "masamune",
            "mjolnir",
            "moltenblade",
            "moltensword",
            "muramasa",
            "mysticalspellblade",
            "mythicblade",
            "partisan",
            "pharaohs_treasure",
            "pheonixgrace",
            "powerfusehammer",
            "powerfusesword",
            "requiem_of_hell",
            "ribboncleaver",
            "righteous_relic",
            "riversofblood",
            "royalchakram",
            "royalrapier",
            "sabre",
            "scissorblade",
            "sculkcleaver",
            "sculkscythe",
            "sculksword",
            "sentinels_will",
            "silverine_blade",
            "soul_collector",
            "soul_devourer",
            "soulclaws",
            "souledge",
            "soulharvester",
            "soulrender",
            "soulstealer",
            "steelsword",
            "stop_sign",
            "stormbringer",
            "storms_edge",
            "sunbreak",
            "tengensblade",
            "terrablade",
            "thousanddemondaggers",
            "thunderbrand",
            "thunderbringer",
            "vampiricneedle",
            "wakizashi",
            "watcher_claymore",
            "watching_warglaive",
            "waxweaver",
            "whisperwind",
            "wickpiercer",
            "yoru"
    };

    private static ItemReplacer instance;
    private final ModeSetting model = new ModeSetting("Item Replacer Model", MODELS[0], MODELS).setVisible(() -> false);
    private final Map<String, ItemStack> previews = new LinkedHashMap<>();
    private Runnable editorOpener = () -> {
    };
    private final ButtonSetting modelGallery = new ButtonSetting("Model", "Opens the item model gallery.", this::openGallery);

    public ItemReplacer() {
        instance = this;
        model.setDescription("Selected replacement sword model.");
        for (String value : MODELS) {
            ItemStack stack = Items.NETHERITE_SWORD.getDefaultStack().copy();
            stack.set(DataComponentTypes.ITEM_MODEL, modelId(value));
            previews.put(value, stack);
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

    public Identifier getSelectedModelId() {
        return modelId(model.getValue());
    }

    public ItemStack apply(ItemStack original) {
        if (!isEnabled() || original == null || original.isEmpty() || !original.isIn(ItemTags.SWORDS)) {
            return original;
        }

        Identifier existing = original.get(DataComponentTypes.ITEM_MODEL);
        if (existing != null && "mre".equals(existing.getNamespace())
                && existing.getPath().startsWith("item_replacer/sword/")) {
            return original;
        }
        ItemStack replacement = original.copy();
        replacement.set(DataComponentTypes.ITEM_MODEL, getSelectedModelId());
        return replacement;
    }

    public void setEditorOpener(Runnable opener) {
        editorOpener = opener == null ? () -> {
        } : opener;
    }

    private void openGallery() {
        editorOpener.run();
    }

    private static Identifier modelId(String value) {
        return Identifier.of("mre", "item_replacer/sword/" + value);
    }
}
