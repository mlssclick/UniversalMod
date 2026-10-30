package universalmod.module.render.richdog;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class PetModel {
    private final ModelPart head, neck, body, frontLeftLeg, frontRightLeg, leftBackLeg, rightBackLeg, tail, leftEar, rightEar;

    public PetModel() {
        ModelPart root = bakeRoot();
        head = root.getChild("head");
        neck = root.getChild("neck");
        body = root.getChild("body");
        frontLeftLeg = root.getChild("front_left_leg");
        frontRightLeg = root.getChild("front_right_leg");
        leftBackLeg = root.getChild("left_back_leg");
        rightBackLeg = root.getChild("right_back_leg");
        tail = root.getChild("tail");
        leftEar = head.getChild("left_ear");
        rightEar = head.getChild("right_ear");
    }

    private static ModelPart bakeRoot() {
        ModelData mesh = new ModelData();
        ModelPartData parts = mesh.getRoot();
        ModelPartData head = parts.addChild("head", ModelPartBuilder.create().uv(0, 0).cuboid(-3, -3, -4, 6, 6, 4).uv(21, 0).cuboid(-1.5F, 0, -7, 3, 3, 3), ModelTransform.pivot(0, 10.5F, -6.8F));
        head.addChild("left_ear", ModelPartBuilder.create().uv(32, 4).cuboid(0, -5, -1.5F, 1, 3, 3).uv(34, 1).cuboid(0, -5.5F, -.75F, 1, 1, 1.5F), ModelTransform.pivot(3, 3, -2));
        head.addChild("right_ear", ModelPartBuilder.create().uv(32, 4).cuboid(-1, -5, -1.5F, 1, 3, 3).uv(34, 1).cuboid(-1, -5.5F, -.75F, 1, 1, 1.5F), ModelTransform.pivot(-3, 3, -2));
        parts.addChild("neck", ModelPartBuilder.create().uv(15, 7).cuboid(-2.95F, -1, -4, 5.9F, 5, 6), ModelTransform.of(0, 10.5F, -5, -25.0F * MathHelper.RADIANS_PER_DEGREE, 0, 0));
        ModelPartData body = parts.addChild("body", ModelPartBuilder.create(), ModelTransform.pivot(0, 13.5F, -5));
        body.addChild("chest", ModelPartBuilder.create().uv(32, 13).cuboid(-4, -3.5F, -3, 8, 7, 6), ModelTransform.pivot(0, 0, 3));
        body.addChild("back", ModelPartBuilder.create().uv(3, 19).cuboid(-3, -3, -.5F, 6, 6, 11), ModelTransform.pivot(0, -.5F, 5.5F));
        parts.addChild("front_left_leg", ModelPartBuilder.create().uv(42, 0).cuboid(-1, 0, -1, 2, 5, 2), ModelTransform.pivot(1.5F, 16, -3));
        parts.addChild("front_right_leg", ModelPartBuilder.create().mirrored().uv(42, 0).cuboid(-1, 0, -1, 2, 5, 2), ModelTransform.pivot(-1.5F, 16, -3));
        parts.addChild("left_back_leg", ModelPartBuilder.create().uv(52, 0).cuboid(-1, 0, -1, 2, 5, 2), ModelTransform.pivot(1.5F, 16, 9));
        parts.addChild("right_back_leg", ModelPartBuilder.create().mirrored().uv(52, 0).cuboid(-1, 0, -1, 2, 5, 2), ModelTransform.pivot(-1.5F, 16, 9));
        parts.addChild("tail", ModelPartBuilder.create().uv(2, 12).cuboid(-1, 2, -1, 2, 8, 2), ModelTransform.of(0, 9, 10, 22.5F * MathHelper.RADIANS_PER_DEGREE, 0, 0));
        return TexturedModelData.of(mesh, 60, 36).createModel();
    }

    public void setupAnim(float ageInTicks, PetBrain brain, FlyPetBrain fly) {
        boolean flyMode = fly != null;
        if (flyMode) {
            animateFly(ageInTicks, fly);
            return;
        }
        leftEar.pitch = leftEar.yaw = leftEar.roll = 0.0F;
        rightEar.pitch = rightEar.yaw = rightEar.roll = 0.0F;
        head.yaw = brain.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        head.pitch = brain.getPitch() * MathHelper.RADIANS_PER_DEGREE;

        float swing = brain.limbSwing * 0.6662F;
        float amount = brain.limbSwingAmount;
        frontLeftLeg.pitch = MathHelper.cos(swing) * 1.4F * amount;
        frontRightLeg.pitch = MathHelper.cos(swing + MathHelper.PI) * 1.4F * amount;
        leftBackLeg.pitch = MathHelper.cos(swing + MathHelper.PI) * 1.4F * amount;
        rightBackLeg.pitch = MathHelper.cos(swing) * 1.4F * amount;

        if (brain.isLay()) {
            frontLeftLeg.pitch = (float) Math.toRadians(-90);
            frontRightLeg.pitch = (float) Math.toRadians(-90);
            leftBackLeg.pitch = (float) Math.toRadians(90);
            rightBackLeg.pitch = (float) Math.toRadians(90);

            frontLeftLeg.yaw = (float) Math.toRadians(-22);
            frontRightLeg.yaw = (float) Math.toRadians(22);
            leftBackLeg.yaw = (float) Math.toRadians(22);
            rightBackLeg.yaw = (float) Math.toRadians(-22);
        } else {
            frontLeftLeg.yaw = frontRightLeg.yaw = leftBackLeg.yaw = rightBackLeg.yaw = 0.0F;
        }

        tail.pitch = (float) Math.toRadians(brain.isLay() ? 45 : 22);
        tail.roll = (float) (Math.toRadians(-22.5F) + 22.5F * MathHelper.RADIANS_PER_DEGREE + MathHelper.cos(ageInTicks * 0.15F) * 0.3F);
    }

    private void animateFly(float ageInTicks, FlyPetBrain fly) {
        head.yaw = fly.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        head.pitch = fly.getPitch() * MathHelper.RADIANS_PER_DEGREE;
        float partialTick = ageInTicks - MathHelper.floor(ageInTicks);
        float wingTime = fly.wingPhase(partialTick);
        float flap = MathHelper.sin(wingTime);
        float secondary = MathHelper.sin(wingTime * 2.0F + 0.6F);
        leftEar.roll = -0.4F - flap * 0.98F;
        rightEar.roll = 0.4F + flap * 0.98F;
        leftEar.pitch = secondary * 0.25F;
        rightEar.pitch = -secondary * 0.25F;
        float stride = fly.legPhase(partialTick);
        float amount = fly.legAmount();
        float swing = MathHelper.cos(stride) * 1.4F * amount;
        frontLeftLeg.pitch = swing;
        frontRightLeg.pitch = -swing;
        leftBackLeg.pitch = -swing;
        rightBackLeg.pitch = swing;
        frontLeftLeg.yaw = -0.10F;
        frontRightLeg.yaw = 0.10F;
        leftBackLeg.yaw = -0.10F;
        rightBackLeg.yaw = 0.10F;
        tail.pitch = 0.38F + MathHelper.cos(ageInTicks * 0.25F) * 0.10F;
        tail.roll = MathHelper.sin(ageInTicks * 0.25F) * 0.2F;
    }

    public void render(MatrixStack pose, VertexConsumer consumer, int packedLight, int packedOverlay,
                       float bodyYaw, boolean laying, FlyPetBrain fly) {
        pose.push();
        pose.translate(0.0F, 1.2F - (laying && fly == null ? 0.3F : 0.0F), 0.0F);
        pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));
        pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw));
        if (fly != null) {
            pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(fly.bodyPitch()));
            pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(fly.bodyRoll()));
        }

        head.render(pose, consumer, packedLight, packedOverlay);
        neck.render(pose, consumer, packedLight, packedOverlay);
        body.render(pose, consumer, packedLight, packedOverlay);
        frontLeftLeg.render(pose, consumer, packedLight, packedOverlay);
        frontRightLeg.render(pose, consumer, packedLight, packedOverlay);
        leftBackLeg.render(pose, consumer, packedLight, packedOverlay);
        rightBackLeg.render(pose, consumer, packedLight, packedOverlay);
        tail.render(pose, consumer, packedLight, packedOverlay);

        pose.pop();
    }
}
