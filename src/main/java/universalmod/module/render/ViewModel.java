package universalmod.module.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.CrossbowItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.HandOffsetEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.NumberSetting;

@ModuleInformation(
        moduleName = "View Model",
        moduleCategory = ModuleCategory.RENDER
)
public final class ViewModel extends Module {
    private static ViewModel instance;

    private final NumberSetting mainHandX = new NumberSetting("Main Hand X", 0.0, -6.0, 6.0, 0.005);
    private final NumberSetting mainHandY = new NumberSetting("Main Hand Y", 0.0, -6.0, 6.0, 0.005);
    private final NumberSetting mainHandZ = new NumberSetting("Main Hand Z", 0.0, -2.5, 2.5, 0.05);
    private final NumberSetting itemSizeMainHand = new NumberSetting("Item Size Main Hand", 1.0, 0.1, 2.6, 0.05);

    private final NumberSetting offHandX = new NumberSetting("Off Hand X", 0.0, -6.0, 6.0, 0.005);
    private final NumberSetting offHandY = new NumberSetting("Off Hand Y", 0.0, -6.0, 6.0, 0.005);
    private final NumberSetting offHandZ = new NumberSetting("Off Hand Z", 0.0, -2.5, 2.5, 0.05);
    private final NumberSetting itemSizeOffHand = new NumberSetting("Item Size Off Hand", 1.0, 0.1, 2.6, 0.05);

    public ViewModel() {
        instance = this;
    }

    public static ViewModel getInstance() {
        return instance;
    }

    @SubscribeEvent
    private void onHandOffset(HandOffsetEvent event) {
        boolean mainHand = event.getHand() == Hand.MAIN_HAND;
        event.setScale(mainHand ? itemSizeMainHand.getFloatValue() : itemSizeOffHand.getFloatValue());

        if (mainHand && event.getStack().getItem() instanceof CrossbowItem) {
            return;
        }

        MatrixStack matrices = event.getMatrices();
        if (mainHand) {
            matrices.translate(mainHandX.getValue(), mainHandY.getValue(), mainHandZ.getValue());
        } else {
            matrices.translate(offHandX.getValue(), offHandY.getValue(), offHandZ.getValue());
        }
    }

    public void applyHandPosition(MatrixStack matrices, Arm arm) {
        if (matrices == null) {
            return;
        }
        if (arm == Arm.RIGHT) {
            matrices.translate(mainHandX.getValue(), mainHandY.getValue(), mainHandZ.getValue());
        } else {
            matrices.translate(offHandX.getValue(), offHandY.getValue(), offHandZ.getValue());
        }
    }
}
