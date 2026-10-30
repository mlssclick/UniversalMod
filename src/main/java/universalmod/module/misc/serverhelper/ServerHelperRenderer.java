package universalmod.module.misc.serverhelper;

import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.misc.ServerHelper;
import universalmod.render.Render3D;
import universalmod.render.color.ColorUtil;

public final class ServerHelperRenderer {
    private static final float BOX_FILL_ALPHA = 0.18F;

    private ServerHelperRenderer() {
    }

    public static void renderWorld(Minecraft mc, WorldRenderEvent event, ServerHelper module) {
        if (!isReady(mc, module)) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
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


    private static boolean isReady(Minecraft mc, ServerHelper module) {
        return mc != null
                && mc.level != null
                && mc.player != null
                && mc.gameRenderer != null
                && module != null
                && module.isEnabled()
                && ServerHelper.isTrackerEnabled();
    }

    private static void renderHeldPreview(Minecraft mc, Camera camera, ServerHelper module) {
        Player player = mc.player;
        if (player == null) {
            return;
        }

        TrapDetectionService.TrapType type = heldType(player);
        if (type == null || !shouldRender(type, module)) {
            return;
        }

        AABB box = TrapDetectionService.createPreviewBox(player.getPosition(1.0F), type);
        if (box != null) {
            renderBox(mc, camera, box, module);
        }
    }

    private static TrapDetectionService.TrapType heldType(Player player) {
        TrapDetectionService.TrapType main = TrapDetectionService.detectType(player.getMainHandItem());
        return main != null ? main : TrapDetectionService.detectType(player.getOffhandItem());
    }

    private static boolean shouldRender(TrapDetectionService.TrapType type, ServerHelper module) {
        return switch (type) {
            case TRAPKA -> module.rendersTrapka();
            case EXPLOSION_TRAP -> module.rendersExplosionTrap();
            case STUN -> module.rendersStun();
        };
    }

    private static void renderBox(Minecraft mc, Camera camera, AABB box, ServerHelper module) {
        int rgb = hasVisibleOtherPlayerInside(mc, camera, mc.player, box) ? module.insideColor() : module.outlineColor();
        int opaque = (rgb & 0x00FFFFFF) | 0xFF000000;
        if (module.fillsBoxes()) {
            int fill = ColorUtil.multAlpha(opaque, BOX_FILL_ALPHA);
            Render3D.drawBox(box, fill, 1.0F, false, true, false);
        }
        Render3D.drawBox(box, opaque, 2.0F, true, false, false);
    }

    private static boolean hasVisibleOtherPlayerInside(Minecraft mc, Camera camera, Player self, AABB box) {
        List<Player> players = mc.level.getEntitiesOfClass(Player.class, box, candidate -> candidate != self);
        for (Player other : players) {
            if (other != null && shouldCountPlayer(other) && isPlayerVisible(mc, camera, other)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldCountPlayer(Player player) {
        if (player.isSpectator() || player.isCreative()) {
            return false;
        }
        return !player.isInvisible() || hasArmor(player);
    }

    private static boolean isPlayerVisible(Minecraft mc, Camera camera, Player player) {
        Vec3 cameraPos = camera.position();
        Vec3 look = cameraLookVector(camera);
        AABB bounds = player.getBoundingBox();
        Vec3 center = bounds.getCenter();
        double halfX = Math.max(0.12D, (bounds.maxX - bounds.minX) * 0.35D);
        double halfZ = Math.max(0.12D, (bounds.maxZ - bounds.minZ) * 0.35D);

        Vec3[] samples = new Vec3[] {
                new Vec3(center.x, bounds.maxY - 0.10D, center.z),
                new Vec3(center.x, bounds.minY + (bounds.maxY - bounds.minY) * 0.66D, center.z),
                new Vec3(center.x, bounds.minY + (bounds.maxY - bounds.minY) * 0.33D, center.z),
                new Vec3(center.x - halfX, bounds.minY + (bounds.maxY - bounds.minY) * 0.58D, center.z),
                new Vec3(center.x + halfX, bounds.minY + (bounds.maxY - bounds.minY) * 0.58D, center.z),
                new Vec3(center.x, bounds.minY + (bounds.maxY - bounds.minY) * 0.58D, center.z - halfZ),
                new Vec3(center.x, bounds.minY + (bounds.maxY - bounds.minY) * 0.58D, center.z + halfZ)
        };

        for (Vec3 sample : samples) {
            Vec3 toSample = sample.subtract(cameraPos);
            if (toSample.dot(look) <= 0.0D) {
                continue;
            }
            if (canSeePoint(mc, cameraPos, sample, player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canSeePoint(Minecraft mc, Vec3 from, Vec3 to, Player ignoredPlayer) {
        HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ignoredPlayer));
        return hit == null || hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(from) + 0.01D >= to.distanceToSqr(from);
    }

    private static Vec3 cameraLookVector(Camera camera) {
        double yaw = Math.toRadians(camera.yRot());
        double pitch = Math.toRadians(camera.xRot());
        double horizontal = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * horizontal, -Math.sin(pitch), Math.cos(yaw) * horizontal).normalize();
    }

    private static boolean hasArmor(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.isArmor() && !player.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

}
