package universalmod.module.misc.serverhelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import universalmod.event.type.WorldRenderEvent;
import universalmod.render.Render3D;
import universalmod.render.color.ColorUtil;

import java.util.List;
import universalmod.module.misc.ServerHelper;

public final class ServerHelperRenderer {
    private static final float BOX_FILL_ALPHA = 0.18F;

    private ServerHelperRenderer() {
    }

    public static void renderWorld(MinecraftClient client, WorldRenderEvent event, ServerHelper settings) {
        if (!isReady(client, settings)) {
            return;
        }
        Camera camera = client.gameRenderer.getCamera();
        renderHeldPreview(client, camera, settings);
        for (TrapDetectionService.TrackedZone zone : TrapDetectionService.getActiveZones()) {
            if (zone.getType() == TrapDetectionService.TrapType.STUN && !settings.remembersStun()) {
                continue;
            }
            if (!shouldRender(zone.getType(), settings)) {
                continue;
            }
            renderBox(client, camera, zone.getBox(), settings);
        }
    }

    private static boolean isReady(MinecraftClient client, ServerHelper settings) {
        return client != null
                && client.world != null
                && client.player != null
                && client.gameRenderer != null
                && settings != null
                && settings.isEnabled() && ServerHelper.isTrackerEnabled();
    }

    private static void renderHeldPreview(MinecraftClient client, Camera camera, ServerHelper settings) {
        PlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        TrapDetectionService.TrapType type = heldType(player);
        if (type == null || !shouldRender(type, settings)) {
            return;
        }
        Box box = TrapDetectionService.createPreviewBox(player.getLerpedPos(1.0F), type);
        if (box != null) {
            renderBox(client, camera, box, settings);
        }
    }

    private static TrapDetectionService.TrapType heldType(PlayerEntity player) {
        TrapDetectionService.TrapType main = TrapDetectionService.detectType(player.getMainHandStack());
        return main != null ? main : TrapDetectionService.detectType(player.getOffHandStack());
    }

    private static boolean shouldRender(TrapDetectionService.TrapType type, ServerHelper settings) {
        return switch (type) {
            case TRAPKA -> settings.rendersTrapka();
            case EXPLOSION_TRAP -> settings.rendersExplosionTrap();
            case STUN -> settings.rendersStun();
        }
                ;
    }

    private static void renderBox(MinecraftClient client, Camera camera, Box box, ServerHelper settings) {
        int rgb = hasVisibleOtherPlayerInside(client, camera, client.player, box)
                ? settings.insideColor()
                : settings.outlineColor();
        int opaque = (rgb & 0x00FFFFFF) | 0xFF000000;
        if (settings.fillsBoxes()) {
            int fill = ColorUtil.multAlpha(opaque, BOX_FILL_ALPHA);
            Render3D.drawFilledBox(box, fill, false);
        }
        Render3D.drawBoxOutline(box, opaque, 2.0F, false);
    }

    private static boolean hasVisibleOtherPlayerInside(MinecraftClient client, Camera camera, PlayerEntity self, Box box) {
        List<PlayerEntity> players = client.world.getEntitiesByClass(PlayerEntity.class, box, candidate -> candidate != self);
        for (PlayerEntity other : players) {
            if (other == null || other == self || !box.intersects(other.getBoundingBox())) {
                continue;
            }
            if (shouldCountPlayer(other) && isPlayerVisible(client, camera, other)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldCountPlayer(PlayerEntity player) {
        if (player.isSpectator() || player.isCreative()) {
            return false;
        }
        return !player.isInvisible() || hasArmor(player);
    }

    private static boolean isPlayerVisible(MinecraftClient client, Camera camera, PlayerEntity player) {
        Vec3d cameraPos = camera.getPos();
        Vec3d look = cameraLookVector(camera);
        Box bounds = player.getBoundingBox();
        Vec3d center = bounds.getCenter();
        double halfX = Math.max(0.12D, (bounds.maxX - bounds.minX) * 0.35D);
        double halfZ = Math.max(0.12D, (bounds.maxZ - bounds.minZ) * 0.35D);
        double height = bounds.maxY - bounds.minY;
        Vec3d[] samples = new Vec3d[]{
                new Vec3d(center.x, bounds.maxY - 0.10D, center.z),
                new Vec3d(center.x, bounds.minY + height * 0.66D, center.z),
                new Vec3d(center.x, bounds.minY + height * 0.33D, center.z),
                new Vec3d(center.x - halfX, bounds.minY + height * 0.58D, center.z),
                new Vec3d(center.x + halfX, bounds.minY + height * 0.58D, center.z),
                new Vec3d(center.x, bounds.minY + height * 0.58D, center.z - halfZ),
                new Vec3d(center.x, bounds.minY + height * 0.58D, center.z + halfZ)
        };
        for (Vec3d sample : samples) {
            Vec3d toSample = sample.subtract(cameraPos);
            if (toSample.dotProduct(look) <= 0.0D) {
                continue;
            }
            if (canSeePoint(client, cameraPos, sample, player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canSeePoint(MinecraftClient client, Vec3d from, Vec3d to, PlayerEntity ignoredPlayer) {
        HitResult hit = client.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ignoredPlayer));
        return hit == null
                || hit.getType() == HitResult.Type.MISS
                || hit.getPos().squaredDistanceTo(from) + 0.01D >= to.squaredDistanceTo(from);
    }

    private static Vec3d cameraLookVector(Camera camera) {
        double yaw = Math.toRadians(camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());
        double horizontal = Math.cos(pitch);
        return new Vec3d(-Math.sin(yaw) * horizontal, -Math.sin(pitch), Math.cos(yaw) * horizontal).normalize();
    }

    private static boolean hasArmor(PlayerEntity player) {
        return !player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                || !player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                || !player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                || !player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }
}
