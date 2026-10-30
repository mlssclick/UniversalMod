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
import universalmod.setting.SliderSetting;

@ModuleInformation(
        moduleName = "View Model",
        moduleCategory = ModuleCategory.RENDER
)
public final class ViewModel extends Module {
    private static ViewModel instance;

    private final SliderSetting mainHandX = new SliderSetting("Main Hand X", 0.0, -6.0, 6.0, 0.005);
    private final SliderSetting mainHandY = new SliderSetting("Main Hand Y", 0.0, -6.0, 6.0, 0.005);
    private final SliderSetting mainHandZ = new SliderSetting("Main Hand Z", 0.0, -2.5, 2.5, 0.05);
    private final SliderSetting itemSizeMainHand = new SliderSetting("Item Size Main Hand", 1.0, 0.1, 2.6, 0.05);

    private final SliderSetting offHandX = new SliderSetting("Off Hand X", 0.0, -6.0, 6.0, 0.005);
    private final SliderSetting offHandY = new SliderSetting("Off Hand Y", 0.0, -6.0, 6.0, 0.005);
    private final SliderSetting offHandZ = new SliderSetting("Off Hand Z", 0.0, -2.5, 2.5, 0.05);
    private final SliderSetting itemSizeOffHand = new SliderSetting("Item Size Off Hand", 1.0, 0.1, 2.6, 0.05);

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
