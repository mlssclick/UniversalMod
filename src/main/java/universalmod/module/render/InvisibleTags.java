package universalmod.module.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;

@ModuleInformation(
        moduleName = "Invisible Tags",
        moduleCategory = ModuleCategory.RENDER
)
public final class InvisibleTags extends Module {
    private static InvisibleTags instance;

    public InvisibleTags() {
        instance = this;
    }

    public static InvisibleTags getInstance() {
        return instance;
    }

    public static boolean shouldShowVanillaInvisibleTag(PlayerEntity player) {
        InvisibleTags invisibleTags = instance;
        return invisibleTags != null && invisibleTags.isEnabled() && invisibleTags.isValidInvisible(player);
    }

    public static boolean applyVanillaInvisibleTag(PlayerEntity player, EntityRenderState state, float tickDelta) {
        if (state == null || !shouldShowVanillaInvisibleTag(player)) {
            return false;
        }
        state.displayName = player.getDisplayName();
        state.nameLabelPos = player.getAttachments().getPointNullable(
                EntityAttachmentType.NAME_TAG,
                0,
                player.getLerpedYaw(tickDelta)
        );
        return true;
    }

    private boolean isValidInvisible(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        if (player == null || player == client.player || player.isRemoved() || player.isSpectator() || !player.isInvisible()) {
            return false;
        }
        if (client.player.squaredDistanceTo(player) > 128.0D * 128.0D) {
            return false;
        }
        Vec3d from = client.player.getEyePos();
        Vec3d to = player.getEyePos();
        BlockHitResult hit = client.world.raycast(new RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }
}
