package universalmod.module.render;

import universalmod.event.SubscribeEvent;

import java.awt.Color;

import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import universalmod.event.type.WorldRenderEvent;
import universalmod.mixin.accessor.IWorldRendererAccessor;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.render.Render3D;
import universalmod.render.Render3D;

@ModuleInformation(
        moduleName = "Custom HitBox",
        moduleCategory = ModuleCategory.RENDER
)
public final class CustomHitBox extends Module {
    private static CustomHitBox instance;

    public final BooleanSetting eyeHitbox = new BooleanSetting("Eye Hitbox", true);
    public final ColorSetting eyeColor = new ColorSetting("Eye Color", new Color(255, 0, 0, 255).getRGB());
    public final BooleanSetting viewingLine = new BooleanSetting("Viewing Line", true);
    public final ColorSetting viewingLineColor = new ColorSetting("Viewing Line Color", new Color(0, 0, 255, 255).getRGB());

    public final BooleanSetting players = new BooleanSetting("Players", true);
    public final ColorSetting playersColor = new ColorSetting("Players Color", Color.WHITE.getRGB());
    public final BooleanSetting animals = new BooleanSetting("Animals", true);
    public final ColorSetting animalsColor = new ColorSetting("Animals Color", Color.WHITE.getRGB());
    public final BooleanSetting monsters = new BooleanSetting("Monsters", true);
    public final ColorSetting monstersColor = new ColorSetting("Monsters Color", Color.WHITE.getRGB());
    public final BooleanSetting throwables = new BooleanSetting("Throwables", true);
    public final ColorSetting throwablesColor = new ColorSetting("Throwables Color", Color.WHITE.getRGB());
    public final BooleanSetting drop = new BooleanSetting("Drop", true);
    public final ColorSetting dropColor = new ColorSetting("Drop Color", Color.WHITE.getRGB());
    public final BooleanSetting misc = new BooleanSetting("Misc", true);
    public final ColorSetting miscColor = new ColorSetting("Misc Color", Color.WHITE.getRGB());

    public CustomHitBox() {
        instance = this;
        eyeColor.setVisible(eyeHitbox::getValue);
        viewingLineColor.setVisible(viewingLine::getValue);
        playersColor.setVisible(players::getValue);
        animalsColor.setVisible(animals::getValue);
        monstersColor.setVisible(monsters::getValue);
        throwablesColor.setVisible(throwables::getValue);
        dropColor.setVisible(drop::getValue);
        miscColor.setVisible(misc::getValue);
    }

    public static CustomHitBox getInstance() {
        return instance;
    }

    public static boolean shouldReplaceVanilla() {
        CustomHitBox module = instance;
        return module != null
                && module.isEnabled()
                && module.mc != null
                && module.mc.getEntityRenderDispatcher().shouldRenderHitboxes();
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        if (!shouldReplaceVanilla() || mc.world == null || mc.player == null) {
            return;
        }

        Frustum frustum = null;
        if (mc.worldRenderer instanceof IWorldRendererAccessor accessor) {
            frustum = accessor.getFrustum();
        }

        for (Entity entity : mc.world.getEntities()) {
            if (!shouldRender(entity, frustum)) {
                continue;
            }

            Integer color = colorFor(entity);
            if (color == null) {
                continue;
            }

            drawEntityBox(entity, color, event.getTickDelta());
            if (eyeHitbox.getValue() && entity instanceof LivingEntity) {
                drawEyeHitbox(entity, eyeColor.getValue(), event.getTickDelta());
            }
            if (viewingLine.getValue()) {
                drawViewingLine(entity, viewingLineColor.getValue(), event.getTickDelta());
            }
        }
    }

    private boolean shouldRender(Entity entity, Frustum frustum) {
        if (entity == null || entity.isRemoved() || entity.isInvisible()) {
            return false;
        }
        if (frustum != null && !frustum.isVisible(entity.getBoundingBox())) {
            return false;
        }
        if (entity == mc.getCameraEntity() && mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            return false;
        }
        return colorFor(entity) != null;
    }

    private Integer colorFor(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return players.getValue() ? playersColor.getValue() : null;
        }
        if (isAnimal(entity)) {
            return animals.getValue() ? animalsColor.getValue() : null;
        }
        if (entity instanceof HostileEntity) {
            return monsters.getValue() ? monstersColor.getValue() : null;
        }
        if (entity instanceof ProjectileEntity) {
            return throwables.getValue() ? throwablesColor.getValue() : null;
        }
        if (entity instanceof ItemEntity) {
            return drop.getValue() ? dropColor.getValue() : null;
        }
        return misc.getValue() ? miscColor.getValue() : null;
    }

    private boolean isAnimal(Entity entity) {
        SpawnGroup group = entity.getType().getSpawnGroup();
        return entity instanceof MerchantEntity
                || group == SpawnGroup.CREATURE
                || group == SpawnGroup.WATER_CREATURE
                || group == SpawnGroup.WATER_AMBIENT
                || group == SpawnGroup.AMBIENT;
    }

    private void drawEntityBox(Entity entity, int color, float tickDelta) {
        Render3D.boxOutline(interpolatedBoundingBox(entity, tickDelta), color, 1.0F, true);
    }

    private void drawEyeHitbox(Entity entity, int color, float tickDelta) {
        Box box = interpolatedBoundingBox(entity, tickDelta);
        double eyeY = box.minY + entity.getEyeHeight(entity.getPose());
        Box eyeBox = new Box(box.minX, eyeY - 0.01D, box.minZ, box.maxX, eyeY + 0.01D, box.maxZ);
        Render3D.boxOutline(eyeBox, color, 1.0F, true);
    }

    private void drawViewingLine(Entity entity, int color, float tickDelta) {
        Vec3d start = entity.getLerpedPos(tickDelta).add(0.0D, entity.getEyeHeight(entity.getPose()), 0.0D);
        Vec3d look = entity.getRotationVec(tickDelta);
        if (look.lengthSquared() <= 1.0E-8D) {
            return;
        }
        Render3D.drawLine(start, start.add(look.multiply(2.0D)), color, 1.0F, true);
    }

    private Box interpolatedBoundingBox(Entity entity, float tickDelta) {
        Box box = entity.getBoundingBox();
        double x = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double z = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());
        return box.offset(x - entity.getX(), y - entity.getY(), z - entity.getZ());
    }
}
