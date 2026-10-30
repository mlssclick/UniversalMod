package universalmod.module.render.richdog;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import universalmod.module.render.richdog.PetAnimation;
import universalmod.util.time.TimerUtil;

import java.util.concurrent.ThreadLocalRandom;

public final class PetBrain {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private Vec3d pos;
    private Vec3d motion = Vec3d.ZERO;
    private float direction = randomAngle();
    private float yaw;
    private float body;
    private final PetAnimation x = new PetAnimation();
    private final PetAnimation y = new PetAnimation();
    private final PetAnimation z = new PetAnimation();
    private final PetAnimation bodyAnim = new PetAnimation();
    private final PetAnimation yawAnim = new PetAnimation();
    private final PetAnimation pitchAnim = new PetAnimation();
    private boolean lay;
    private final TimerUtil staying = new TimerUtil();
    public float prevLimbSwingAmount;
    public float limbSwingAmount;
    public float limbSwing;
    private PlayerEntity entity;
    private LivingEntity target;

    public void setEntity(PlayerEntity entity) {
        this.entity = entity;
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    public void onUpdate() {
        if (entity == null || mc.world == null) return;
        Vec3d playerPos = entity.getPos();
        if (pos == null || pos.distanceTo(playerPos) > 10.0D) {
            pos = playerPos;
            x.animate((float) pos.x, 1);
            y.animate((float) pos.y, 1);
            z.animate((float) pos.z, 1);
        }
        motion = motion.add(0.0D, -0.2D, 0.0D);
        Vec3d newPos = pos.add(motion);
        if (isBlockSolid(newPos.x, newPos.y, newPos.z)) {
            int blockY = (int) newPos.y;
            newPos = new Vec3d(newPos.x, blockY + 1.0D + 0.1D, newPos.z);
            motion = new Vec3d(motion.x, 0.0D, motion.z);
        }
        if (collidesHorizontally(newPos)) {
            newPos = new Vec3d(pos.x, newPos.y, pos.z);
            motion = new Vec3d(-motion.x * 0.35D, motion.y, -motion.z * 0.35D);
            direction = randomAngle();
        }
        motion = new Vec3d(motion.x, 0.0D, motion.z);
        LivingEntity currentTarget = target;
        if (currentTarget != null && entity instanceof ClientPlayerEntity) {
            if (isBlockSolid(newPos.x, newPos.y - 0.1D, newPos.z)) motion = new Vec3d(motion.x, 0.62D, motion.z);
            Box box = new Box(getPos().subtract(new Vec3d(0.4D, 0.0D, 0.4D)), getPos().add(0.4D, 0.4D, 0.4D));
            Box targetBox = currentTarget.getBoundingBox().expand(-0.1D, 0.0D, -0.1D);
            Vec3d targetDirection = currentTarget.getPos().subtract(newPos);
            if (targetDirection.lengthSquared() > 1.0E-9D) motion = motion.add(targetDirection.normalize());
            if (box.maxX > targetBox.minX && box.maxY > targetBox.minY && box.maxZ > targetBox.minZ
                    && box.minX < targetBox.maxX && box.minY < targetBox.maxY && box.minZ < targetBox.maxZ) {
                motion = motion.multiply(-1.0D, 1.0D, -1.0D);
            }
        } else if (newPos.distanceTo(playerPos) > 2.0D) {
            Vec3d ownerDirection = playerPos.subtract(newPos);
            if (ownerDirection.lengthSquared() > 1.0E-9D) motion = motion.add(ownerDirection.normalize());
        }
        handleRotation();
        pos = newPos;
        if (pos.distanceTo(playerPos) < 0.1D) {
            direction = randomAngle();
            double xMot = -Math.sin(Math.toRadians(direction)) * 0.1D;
            double zMot = Math.cos(Math.toRadians(direction)) * 0.1D;
            motion = motion.add(xMot, 0.0D, zMot);
        }
        motion = motion.multiply(0.5D);
        int speed = 150;
        x.animate((float) pos.x, speed);
        y.animate((float) pos.y, speed);
        z.animate((float) pos.z, speed);
        limbTick();
        if (Math.abs(pos.x - x.get()) > 0.1F || Math.abs(pos.z - z.get()) > 0.1F) staying.reset();
        lay = staying.isReached(1000L);
    }

    private void handleRotation() {
        if (motion.x != 0.0D || motion.z != 0.0D) {
            double angle = Math.atan2(motion.z, motion.x);
            yaw = (float) Math.toDegrees(angle) - 90.0F;
            yaw %= 360.0F;
            if (yaw < 0.0F) yaw += 360.0F;
        }
        float[] rotation = lookAt(pos, entity.getEyePos());
        LivingEntity currentTarget = target;
        if (currentTarget != null && entity instanceof ClientPlayerEntity)
            rotation = lookAt(pos, currentTarget.getPos());
        float gradus = lay ? 200.0F : 150.0F;
        float gradus1 = lay ? 100.0F : 50.0F;
        if (rotation[0] - yaw < -gradus || rotation[0] - yaw > gradus) yaw = rotation[0];
        float shortestYawPath = (((yaw - body) % 360.0F + 540.0F) % 360.0F) - 180.0F;
        if (!lay) bodyAnim.animate(body + shortestYawPath, 150);
        yawAnim.animate(MathHelper.clamp(rotation[0] - yaw, -gradus1, gradus1), 150);
        pitchAnim.animate(rotation[1], 150);
        body += shortestYawPath;
    }

    private void limbTick() {
        prevLimbSwingAmount = limbSwingAmount;
        double d0 = x.get() - pos.x, d2 = z.get() - pos.z;
        float f = MathHelper.sqrt((float) (d0 * d0 + d2 * d2)) * 4.0F;
        if (f > 1.0F) f = 1.0F;
        limbSwingAmount += (f - limbSwingAmount) * 0.4F;
        limbSwing += limbSwingAmount;
    }

    public float getBody() {
        return bodyAnim.get();
    }

    public float getYaw() {
        return yawAnim.get();
    }

    public float getPitch() {
        return pitchAnim.get();
    }

    public boolean isLay() {
        return lay;
    }

    public Vec3d getPos() {
        return new Vec3d(x.get(), y.get(), z.get());
    }

    private static float[] lookAt(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z, dxz = Math.sqrt(dx * dx + dz * dz);
        return new float[]{(float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F, (float) Math.toDegrees(-Math.atan2(dy, dxz))};
    }

    private static float randomAngle() {
        return ThreadLocalRandom.current().nextFloat() * 360.0F;
    }

    private boolean isBlockSolid(double bx, double by, double bz) {
        if (mc.world == null) return false;
        BlockPos bp = BlockPos.ofFloored(bx, by, bz);
        return !mc.world.getBlockState(bp).getCollisionShape(mc.world, bp).isEmpty();
    }

    private boolean collidesHorizontally(Vec3d position) {
        double radius = .35D, baseY = horizontalBaseY(position);
        double[] heights = {0.0D, .45D};
        for (double height : heights) {
            double sy = baseY + height;
            if (isBlockSolid(position.x + radius, sy, position.z + radius) || isBlockSolid(position.x + radius, sy, position.z - radius) || isBlockSolid(position.x - radius, sy, position.z + radius) || isBlockSolid(position.x - radius, sy, position.z - radius))
                return true;
        }
        return false;
    }

    private double horizontalBaseY(Vec3d position) {
        double supportRadius = .32D, probeY = position.y - .2D, highest = Double.NEGATIVE_INFINITY;
        double[][] offsets = {{0, 0}, {supportRadius, supportRadius}, {supportRadius, -supportRadius}, {-supportRadius, supportRadius}, {-supportRadius, -supportRadius}};
        for (double[] off : offsets)
            if (isBlockSolid(position.x + off[0], probeY, position.z + off[1]))
                highest = Math.max(highest, Math.floor(probeY) + 1.0D);
        if (highest == Double.NEGATIVE_INFINITY) return position.y + .05D;
        return Math.max(position.y + .05D, highest + .05D);
    }
}
