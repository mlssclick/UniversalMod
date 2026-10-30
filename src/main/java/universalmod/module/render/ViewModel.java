package universalmod.module.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.HandOffsetEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.NumberSetting;

public class ViewModel extends Module {
    private final NumberSetting mainHandX = register(new NumberSetting("Main Hand X", "Main hand X offset.", 0.0, -6.0, 6.0, 0.005));
    private final NumberSetting mainHandY = register(new NumberSetting("Main Hand Y", "Main hand Y offset.", 0.0, -6.0, 6.0, 0.005));
    private final NumberSetting mainHandZ = register(new NumberSetting("Main Hand Z", "Main hand Z offset.", 0.0, -2.5, 2.5, 0.05));
    private final NumberSetting itemSizeMainHand = register(new NumberSetting("Item Size Main Hand", "Rendered item size in the main hand.", 1.0, 0.1, 2.6, 0.05));
    private final NumberSetting offHandX = register(new NumberSetting("Off Hand X", "Off hand X offset.", 0.0, -6.0, 6.0, 0.005));
    private final NumberSetting offHandY = register(new NumberSetting("Off Hand Y", "Off hand Y offset.", 0.0, -6.0, 6.0, 0.005));
    private final NumberSetting offHandZ = register(new NumberSetting("Off Hand Z", "Off hand Z offset.", 0.0, -2.5, 2.5, 0.05));
    private final NumberSetting itemSizeOffHand = register(new NumberSetting("Item Size Off Hand", "Rendered item size in the off hand.", 1.0, 0.1, 2.6, 0.05));

    public ViewModel() {
        super("view_model", "View Model", ModuleCategory.RENDER);
    }

    @SubscribeEvent
    private void onHandOffset(HandOffsetEvent event) {
        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;
        event.setScale(mainHand ? itemSizeMainHand.getFloat() : itemSizeOffHand.getFloat());

        if (mainHand && event.getStack().getItem() instanceof CrossbowItem) {
            return;
        }

        PoseStack matrices = event.getMatrices();
        if (mainHand) {
            matrices.translate(mainHandX.getValue(), mainHandY.getValue(), mainHandZ.getValue());
        } else {
            matrices.translate(offHandX.getValue(), offHandY.getValue(), offHandZ.getValue());
        }
    }
}
