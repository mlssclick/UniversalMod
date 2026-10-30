package universalmod.module.render;

import universalmod.event.SubscribeEvent;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import universalmod.event.type.HandAnimationEvent;
import universalmod.event.type.SwingDurationEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ModeSetting;
import universalmod.setting.NumberSetting;

@ModuleInformation(
        moduleName = "Swing Animation",
        moduleCategory = ModuleCategory.RENDER
)
public final class SwingAnimation extends Module {
    private static SwingAnimation instance;
    private final ModeSetting swingType = new ModeSetting("Type", "Chop",
            "Chop", "Swipe", "Down", "Smooth", "Smooth 2", "Power", "Feast", "Twist", "Stab", "Helix", "Default");
    private final NumberSetting hitStrength = new NumberSetting("Strength", 1.0, 0.5, 3.0, 0.05);
    private final NumberSetting swingSpeed = new NumberSetting("Duration", 1.0, 0.5, 4.0, 0.05);
    private final BooleanSetting onlySwing = new BooleanSetting("Only Swing", false);

    public SwingAnimation() {
        instance = this;
    }

    public static SwingAnimation getInstance() {
        return instance;
    }

    @SubscribeEvent
    private void onSwingDuration(SwingDurationEvent event) {
        if (isBlockedForSwingAnimation(Hand.MAIN_HAND) || isBlockedForSwingAnimation(Hand.OFF_HAND)) return;
        event.setAnimation(swingSpeed.getFloatValue());
        event.cancel();
    }

    @SubscribeEvent
    private void onHandAnimation(HandAnimationEvent event) {
        if (applyAnimation(event.getMatrices(), event.getHand(), event.getSwingProgress())) {
            event.cancel();
        }
    }

    public boolean applyAnimation(net.minecraft.client.util.math.MatrixStack matrices, Hand hand, float swingProgress) {
        if (!isEnabled() || mc.player == null || hand != Hand.MAIN_HAND || isBlockedForSwingAnimation(hand))
            return false;
        int armSide = mc.player.getMainArm() == Arm.RIGHT ? 1 : -1;
        float sin1 = MathHelper.sin(swingProgress * swingProgress * MathHelper.PI);
        float sin2 = MathHelper.sin(MathHelper.sqrt(swingProgress) * MathHelper.PI);
        float sinSmooth = (float) (Math.sin(swingProgress * Math.PI) * 0.5F);
        float strength = hitStrength.getFloatValue();

        if (onlySwing.getValue() && mc.player.handSwingTicks == 0) {
            matrices.translate(armSide * 0.56F, -0.52F, -0.72F);
        } else if (swingType.is("Chop")) {
            matrices.translate(0.56F * armSide, -0.44F, -0.72F);
            matrices.translate(0.0F, 0.33F * -0.6F, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * armSide));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin2 * -20.0F * armSide * strength));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -80.0F * strength));
            matrices.translate(0.4F, 0.2F, 0.2F);
            matrices.translate(-0.5F, 0.08F, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(20.0F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(20.0F));
        } else if (swingType.is("Twist")) {
            matrices.translate(armSide * 0.56F, -0.36F, -0.72F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(80.0F * armSide));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -90.0F * strength));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((sin1 - sin2) * 60.0F * armSide * strength));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30.0F));
            matrices.translate(0.0F, -0.1F, 0.05F);
        } else if (swingType.is("Swipe")) {
            matrices.translate(0.56F * armSide, -0.32F, -0.72F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(70.0F * armSide));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20.0F * armSide));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((sin2 * sin1) * -5.0F * strength));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin2 * sin1) * -120.0F * strength));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-70.0F));
        } else if (swingType.is("Default")) {
            matrices.translate(armSide * 0.56F, -0.52F - sin2 * 0.5F * strength, -0.72F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * armSide));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45.0F * armSide));
        } else if (swingType.is("Down")) {
            matrices.translate(armSide * 0.56F, -0.32F, -0.72F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(76.0F * armSide));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * -5.0F * strength));
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(sin2 * -100.0F * strength));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -155.0F * strength));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100.0F));
        } else if (swingType.is("Smooth")) {
            matrices.translate(armSide * 0.56F, -0.42F, -0.72F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSide * (45.0F + sin1 * -20.0F * strength)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSide * sin2 * -20.0F * strength));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -80.0F * strength));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSide * -45.0F));
            matrices.translate(0.0F, -0.1F, 0.0F);
        } else if (swingType.is("Smooth 2")) {
            matrices.translate(armSide * 0.56F, -0.42F, -0.72F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -80.0F * strength));
            matrices.translate(0.0F, -0.1F, 0.0F);
        } else if (swingType.is("Power")) {
            matrices.translate(armSide * 0.56F, -0.32F, -0.72F);
            matrices.translate((-sinSmooth * sinSmooth * sin1) * armSide * strength, 0.0F, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(61.0F * armSide));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin2 * strength));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((sin2 * sin1) * -5.0F * strength));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin2 * sin1) * -30.0F * strength));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-60.0F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinSmooth * -60.0F * strength));
        } else if (swingType.is("Feast")) {
            matrices.translate(armSide * 0.56F, -0.32F, -0.72F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30.0F * armSide));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * 75.0F * armSide * strength));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -45.0F * strength));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30.0F * armSide));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35.0F * armSide));
        } else if (swingType.is("Stab")) {
            matrices.translate(armSide * 0.66F, -0.32F, -0.92F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(25 * armSide));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * 90 * armSide * strength));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -15 * strength));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(50 * armSide));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(70 * armSide));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(2 * armSide));
        } else if (swingType.is("Helix")) {
            matrices.translate(armSide * 0.66F, -0.32F, -0.92F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35 * armSide));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * 15 * armSide * strength));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -55 * strength));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(50 * armSide));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(70 * armSide));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(2 * armSide));
        }
        return true;
    }

    private boolean isBlockedForSwingAnimation(Hand hand) {
        return mc.player != null && isExcludedSwingItem(mc.player.getStackInHand(hand));
    }

    private boolean isExcludedSwingItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof TridentItem) return true;
        return Registries.ITEM.getId(stack.getItem()).getPath().contains("spear");
    }
}
