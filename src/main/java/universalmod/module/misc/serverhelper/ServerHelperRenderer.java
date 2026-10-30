package universalmod.module.misc.serverhelper;

import java.util.List;
import net.minecraft.client.render.Camera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.misc.ServerHelper;
import universalmod.render.Render3D;
import universalmod.render.color.ColorUtil;

public final class ServerHelperRenderer {
    private static final float BOX_FILL_ALPHA = 0.18F;

    private ServerHelperRenderer() {
    }

    public static void renderWorld(MinecraftClient mc, WorldRenderEvent event, ServerHelper module) {
        if (!isReady(mc, module)) {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        renderHeldPreview(mc, camera, module);
        for (TrapDetectionService.TrackedZone zone : TrapDetectionService.getActiveZones()) {
            if (zone.getType() == TrapDetectionService.TrapType.STUN && !module.remembersStun()) {
                continue;
            }
            if (!shouldRender(zone.getType(), module)) {
                continue;
            }
            renderBox(mc, camera, zone.getBox(), module);
        }
    }


    private static boolean isReady(MinecraftClient mc, ServerHelper module) {
        return mc != null
                && mc.world != null
                && mc.player != null
                && mc.gameRenderer != null
                && module != null
                && module.isEnabled()
                && ServerHelper.isTrackerEnabled();
    }

    private static void renderHeldPreview(MinecraftClient mc, Camera camera, ServerHelper module) {
        PlayerEntity player = mc.player;
        if (player == null) {
            return;
        }

        TrapDetectionService.TrapType type = heldType(player);
        if (type == null || !shouldRender(type, module)) {
            return;
        }

        Box box = TrapDetectionService.createPreviewBox(player.getLerpedPos(1.0F), type);
        if (box != null) {
            renderBox(mc, camera, box, module);
        }
    }

    private static TrapDetectionService.TrapType heldType(PlayerEntity player) {
        TrapDetectionService.TrapType main = TrapDetectionService.detectType(player.getMainHandStack());
        return main != null ? main : TrapDetectionService.detectType(player.getOffHandStack());
    }

    private static boolean shouldRender(TrapDetectionService.TrapType type, ServerHelper module) {
        return switch (type) {
            case TRAPKA -> module.rendersTrapka();
            case EXPLOSION_TRAP -> module.rendersExplosionTrap();
            case STUN -> module.rendersStun();
        };
    }

    private static void renderBox(MinecraftClient mc, Camera camera, Box box, ServerHelper module) {
        int rgb = hasVisibleOtherPlayerInside(mc, camera, mc.player, box) ? module.insideColor() : module.outlineColor();
        int opaque = (rgb & 0x00FFFFFF) | 0xFF000000;
        if (module.fillsBoxes()) {
            int fill = ColorUtil.multAlpha(opaque, BOX_FILL_ALPHA);
            Render3D.drawBox(box, fill, 1.0F, false, true, false);
        }
        Render3D.drawBox(box, opaque, 2.0F, true, false, false);
    }

    private static boolean hasVisibleOtherPlayerInside(MinecraftClient mc, Camera camera, PlayerEntity self, Box box) {
        List<PlayerEntity> players = mc.world.getEntitiesByClass(PlayerEntity.class, box, candidate -> candidate != self);
        for (PlayerEntity other : players) {
            if (other != null && shouldCountPlayer(other) && isPlayerVisible(mc, camera, other)) {
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

    private static boolean isPlayerVisible(MinecraftClient mc, Camera camera, PlayerEntity player) {
        Vec3d cameraPos = camera.getPos();
        Vec3d look = cameraLookVector(camera);
        Box bounds = player.getBoundingBox();
        Vec3d center = bounds.getCenter();
        double halfX = Math.max(0.12D, (bounds.maxX - bounds.minX) * 0.35D);
        double halfZ = Math.max(0.12D, (bounds.maxZ - bounds.minZ) * 0.35D);

        Vec3d[] samples = new Vec3d[] {
                new Vec3d(center.x, bounds.maxY - 0.10D, center.z),
                new Vec3d(center.x, bounds.minY + (bounds.maxY - bounds.minY) * 0.66D, center.z),
                new Vec3d(center.x, bounds.minY + (bounds.maxY - bounds.minY) * 0.33D, center.z),
                new Vec3d(center.x - halfX, bounds.minY + (bounds.maxY - bounds.minY) * 0.58D, center.z),
                new Vec3d(center.x + halfX, bounds.minY + (bounds.maxY - bounds.minY) * 0.58D, center.z),
                new Vec3d(center.x, bounds.minY + (bounds.maxY - bounds.minY) * 0.58D, center.z - halfZ),
                new Vec3d(center.x, bounds.minY + (bounds.maxY - bounds.minY) * 0.58D, center.z + halfZ)
        };

        for (Vec3d sample : samples) {
            Vec3d toSample = sample.subtract(cameraPos);
            if (toSample.dotProduct(look) <= 0.0D) {
                continue;
            }
            if (canSeePoint(mc, cameraPos, sample, player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canSeePoint(MinecraftClient mc, Vec3d from, Vec3d to, PlayerEntity ignoredPlayer) {
        HitResult hit = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ignoredPlayer));
        return hit == null || hit.getType() == HitResult.Type.MISS || hit.getPos().squaredDistanceTo(from) + 0.01D >= to.squaredDistanceTo(from);
    }

    private static Vec3d cameraLookVector(Camera camera) {
        double yaw = Math.toRadians(camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());
        double horizontal = Math.cos(pitch);
        return new Vec3d(-Math.sin(yaw) * horizontal, -Math.sin(pitch), Math.cos(yaw) * horizontal).normalize();
    }

    private static boolean hasArmor(PlayerEntity player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.isArmorSlot() && !player.getEquippedStack(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

}
