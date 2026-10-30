package universalmod.module.render.richdog;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public final class PetModel {
    private final ModelPart head;
    private final ModelPart neck;
    private final ModelPart body;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart leftBackLeg;
    private final ModelPart rightBackLeg;
    private final ModelPart tail;
    private final ModelPart leftEar;
    private final ModelPart rightEar;

    public PetModel() {
        ModelPart root = bakeRoot();
        this.head = root.getChild("head");
        this.neck = root.getChild("neck");
        this.body = root.getChild("body");
        this.frontLeftLeg = root.getChild("front_left_leg");
        this.frontRightLeg = root.getChild("front_right_leg");
        this.leftBackLeg = root.getChild("left_back_leg");
        this.rightBackLeg = root.getChild("right_back_leg");
        this.tail = root.getChild("tail");
        this.leftEar = head.getChild("left_ear");
        this.rightEar = head.getChild("right_ear");
    }

    private static ModelPart bakeRoot() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();

        PartDefinition head = parts.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, -3.0F, -4.0F, 6.0F, 6.0F, 4.0F)
                        .texOffs(21, 0).addBox(-1.5F, 0.0F, -7.0F, 3.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 10.5F, -6.8F));

        head.addOrReplaceChild("left_ear",
                CubeListBuilder.create()
                        .texOffs(32, 4).addBox(0.0F, -5.0F, -1.5F, 1.0F, 3.0F, 3.0F)
                        .texOffs(34, 1).addBox(0.0F, -5.5F, -0.75F, 1.0F, 1.0F, 1.5F),
                PartPose.offset(3.0F, 3.0F, -2.0F));

        head.addOrReplaceChild("right_ear",
                CubeListBuilder.create()
                        .texOffs(32, 4).addBox(-1.0F, -5.0F, -1.5F, 1.0F, 3.0F, 3.0F)
                        .texOffs(34, 1).addBox(-1.0F, -5.5F, -0.75F, 1.0F, 1.0F, 1.5F),
                PartPose.offset(-3.0F, 3.0F, -2.0F));

        parts.addOrReplaceChild("neck",
                CubeListBuilder.create()
                        .texOffs(15, 7).addBox(-2.95F, -1.0F, -4.0F, 5.9F, 5.0F, 6.0F),
                PartPose.offsetAndRotation(0.0F, 10.5F, -5.0F, -25.0F * Mth.DEG_TO_RAD, 0.0F, 0.0F));

        PartDefinition body = parts.addOrReplaceChild("body",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 13.5F, -5.0F));

        body.addOrReplaceChild("chest",
                CubeListBuilder.create()
                        .texOffs(32, 13).addBox(-4.0F, -3.5F, -3.0F, 8.0F, 7.0F, 6.0F),
                PartPose.offset(0.0F, 0.0F, 3.0F));

        body.addOrReplaceChild("back",
                CubeListBuilder.create()
                        .texOffs(3, 19).addBox(-3.0F, -3.0F, -0.5F, 6.0F, 6.0F, 11.0F),
                PartPose.offset(0.0F, -0.5F, 5.5F));

        parts.addOrReplaceChild("front_left_leg",
                CubeListBuilder.create()
                        .texOffs(42, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offset(1.5F, 16.0F, -3.0F));

        parts.addOrReplaceChild("front_right_leg",
                CubeListBuilder.create().mirror()
                        .texOffs(42, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offset(-1.5F, 16.0F, -3.0F));

        parts.addOrReplaceChild("left_back_leg",
                CubeListBuilder.create()
                        .texOffs(52, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offset(1.5F, 16.0F, 9.0F));

        parts.addOrReplaceChild("right_back_leg",
                CubeListBuilder.create().mirror()
                        .texOffs(52, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offset(-1.5F, 16.0F, 9.0F));

        parts.addOrReplaceChild("tail",
                CubeListBuilder.create()
                        .texOffs(2, 12).addBox(-1.0F, 2.0F, -1.0F, 2.0F, 8.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 9.0F, 10.0F, 22.5F * Mth.DEG_TO_RAD, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 60, 36).bakeRoot();
    }

    public void setupAnim(float ageInTicks, PetBrain brain, FlyPetBrain fly) {
        boolean flyMode = fly != null;
        if (flyMode) {
            animateFly(ageInTicks, fly);
            return;
        }
        leftEar.xRot = leftEar.yRot = leftEar.zRot = 0.0F;
        rightEar.xRot = rightEar.yRot = rightEar.zRot = 0.0F;
        head.yRot = brain.getYaw() * Mth.DEG_TO_RAD;
        head.xRot = brain.getPitch() * Mth.DEG_TO_RAD;

        float swing = brain.limbSwing * 0.6662F;
        float amount = brain.limbSwingAmount;
        frontLeftLeg.xRot = Mth.cos(swing) * 1.4F * amount;
        frontRightLeg.xRot = Mth.cos(swing + Mth.PI) * 1.4F * amount;
        leftBackLeg.xRot = Mth.cos(swing + Mth.PI) * 1.4F * amount;
        rightBackLeg.xRot = Mth.cos(swing) * 1.4F * amount;

        if (brain.isLay()) {
            frontLeftLeg.xRot = (float) Math.toRadians(-90);
            frontRightLeg.xRot = (float) Math.toRadians(-90);
            leftBackLeg.xRot = (float) Math.toRadians(90);
            rightBackLeg.xRot = (float) Math.toRadians(90);

            frontLeftLeg.yRot = (float) Math.toRadians(-22);
            frontRightLeg.yRot = (float) Math.toRadians(22);
            leftBackLeg.yRot = (float) Math.toRadians(22);
            rightBackLeg.yRot = (float) Math.toRadians(-22);
        } else {
            frontLeftLeg.yRot = frontRightLeg.yRot = leftBackLeg.yRot = rightBackLeg.yRot = 0.0F;
        }

        tail.xRot = (float) Math.toRadians(brain.isLay() ? 45 : 22);
        tail.zRot = (float) (Math.toRadians(-22.5F) + 22.5F * Mth.DEG_TO_RAD + Mth.cos(ageInTicks * 0.15F) * 0.3F);
    }

    private void animateFly(float ageInTicks, FlyPetBrain fly) {
        head.yRot = fly.getYaw() * Mth.DEG_TO_RAD;
        head.xRot = fly.getPitch() * Mth.DEG_TO_RAD;
        float partialTick = ageInTicks - Mth.floor(ageInTicks);
        float wingTime = fly.wingPhase(partialTick);
        float flap = Mth.sin(wingTime);
        float secondary = Mth.sin(wingTime * 2.0F + 0.6F);
        leftEar.zRot = -0.4F - flap * 0.98F;
        rightEar.zRot = 0.4F + flap * 0.98F;
        leftEar.xRot = secondary * 0.25F;
        rightEar.xRot = -secondary * 0.25F;
        float stride = fly.legPhase(partialTick);
        float amount = fly.legAmount();
        float swing = Mth.cos(stride) * 1.4F * amount;
        frontLeftLeg.xRot = swing;
        frontRightLeg.xRot = -swing;
        leftBackLeg.xRot = -swing;
        rightBackLeg.xRot = swing;
        frontLeftLeg.yRot = -0.10F;
        frontRightLeg.yRot = 0.10F;
        leftBackLeg.yRot = -0.10F;
        rightBackLeg.yRot = 0.10F;
        tail.xRot = 0.38F + Mth.cos(ageInTicks * 0.25F) * 0.10F;
        tail.zRot = Mth.sin(ageInTicks * 0.25F) * 0.2F;
    }

    public void render(PoseStack pose, VertexConsumer consumer, int packedLight, int packedOverlay,
                       float bodyYaw, boolean laying, FlyPetBrain fly) {
        pose.pushPose();
        pose.translate(0.0F, 1.2F - (laying && fly == null ? 0.3F : 0.0F), 0.0F);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180.0F));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(bodyYaw));
        if (fly != null) {
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(fly.bodyPitch()));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(fly.bodyRoll()));
        }

        head.render(pose, consumer, packedLight, packedOverlay);
        neck.render(pose, consumer, packedLight, packedOverlay);
        body.render(pose, consumer, packedLight, packedOverlay);
        frontLeftLeg.render(pose, consumer, packedLight, packedOverlay);
        frontRightLeg.render(pose, consumer, packedLight, packedOverlay);
        leftBackLeg.render(pose, consumer, packedLight, packedOverlay);
        rightBackLeg.render(pose, consumer, packedLight, packedOverlay);
        tail.render(pose, consumer, packedLight, packedOverlay);

        pose.popPose();
    }
}
