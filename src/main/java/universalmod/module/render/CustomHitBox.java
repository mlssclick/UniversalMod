package universalmod.module.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.option.Perspective;
import universalmod.mixin.accessor.IWorldRendererAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.render.Render3D;

public final class CustomHitBox extends Module {
    private static CustomHitBox instance;
    private final BooleanSetting eyeHitbox = register(new BooleanSetting("Eye Hitbox", "Draw eye height plane.", true));
    private final ColorSetting eyeColor = register(new ColorSetting("Eye Color", "Eye hitbox color.", 0xFFFF0000));
    private final BooleanSetting viewingLine = register(new BooleanSetting("Viewing Line", "Draw entity look direction.", true));
    private final ColorSetting viewingLineColor = register(new ColorSetting("Viewing Line Color", "Viewing line color.", 0xFF0000FF));
    private final BooleanSetting players = register(new BooleanSetting("Players", "Render player hitboxes.", true));
    private final ColorSetting playersColor = register(new ColorSetting("Players Color", "Player hitbox color.", 0xFFFFFFFF));
    private final BooleanSetting animals = register(new BooleanSetting("Animals", "Render passive mob hitboxes.", true));
    private final ColorSetting animalsColor = register(new ColorSetting("Animals Color", "Animal hitbox color.", 0xFFFFFFFF));
    private final BooleanSetting monsters = register(new BooleanSetting("Monsters", "Render monster hitboxes.", true));
    private final ColorSetting monstersColor = register(new ColorSetting("Monsters Color", "Monster hitbox color.", 0xFFFFFFFF));
    private final BooleanSetting throwables = register(new BooleanSetting("Throwables", "Render projectile hitboxes.", true));
    private final ColorSetting throwablesColor = register(new ColorSetting("Throwables Color", "Projectile hitbox color.", 0xFFFFFFFF));
    private final BooleanSetting drop = register(new BooleanSetting("Drop", "Render dropped item hitboxes.", true));
    private final ColorSetting dropColor = register(new ColorSetting("Drop Color", "Dropped item hitbox color.", 0xFFFFFFFF));
    private final BooleanSetting misc = register(new BooleanSetting("Misc", "Render miscellaneous entity hitboxes.", true));
    private final ColorSetting miscColor = register(new ColorSetting("Misc Color", "Miscellaneous hitbox color.", 0xFFFFFFFF));

    public CustomHitBox() {
        super("Custom HitBox", ModuleCategory.RENDER);
        instance = this;
        eyeColor.visibleWhen(eyeHitbox::getValue);
        viewingLineColor.visibleWhen(viewingLine::getValue);
        playersColor.visibleWhen(players::getValue);
        animalsColor.visibleWhen(animals::getValue);
        monstersColor.visibleWhen(monsters::getValue);
        throwablesColor.visibleWhen(throwables::getValue);
        dropColor.visibleWhen(drop::getValue);
        miscColor.visibleWhen(misc::getValue);
    }

    public static CustomHitBox getInstance() {
        return instance;
    }

    public static boolean shouldReplaceVanilla(MinecraftClient c) {
        return instance != null && instance.isEnabled() && c != null && c.world != null && c.getEntityRenderDispatcher().shouldRenderHitboxes();
    }

    @SubscribeEvent
    private void render(WorldRenderEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!shouldReplaceVanilla(client) || client.player == null) return;
        Frustum frustum = client.worldRenderer instanceof IWorldRendererAccessor accessor
                ? accessor.getFrustum() : null;
        for (Entity entity : client.world.getEntities()) {
            if (!shouldRender(client, entity, frustum)) continue;
            Integer color = colorFor(entity);
            if (color == null) continue;
            Render3D.drawBoxOutline(interpolated(entity, event.getTickDelta()), color, 1.0F, true);
            if (eyeHitbox.getValue() && entity instanceof LivingEntity)
                drawEyeHitbox(entity, event.getTickDelta());
            if (viewingLine.getValue())
                drawViewingLine(entity, event.getTickDelta());
        }
    }

    private boolean shouldRender(MinecraftClient client, Entity entity, Frustum frustum) {
        if (entity == null || entity.isRemoved() || entity.isInvisible()) return false;
        if (frustum != null && !frustum.isVisible(entity.getBoundingBox())) return false;
        if (entity == client.getCameraEntity() && client.options.getPerspective() == Perspective.FIRST_PERSON)
            return false;
        return colorFor(entity) != null;
    }

    private Integer colorFor(Entity entity) {
        if (entity instanceof PlayerEntity) return players.getValue() ? playersColor.getValue() : null;
        if (isAnimal(entity)) return animals.getValue() ? animalsColor.getValue() : null;
        if (entity instanceof HostileEntity) return monsters.getValue() ? monstersColor.getValue() : null;
        if (entity instanceof ProjectileEntity) return throwables.getValue() ? throwablesColor.getValue() : null;
        if (entity instanceof ItemEntity) return drop.getValue() ? dropColor.getValue() : null;
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

    private void drawEyeHitbox(Entity entity, float tickDelta) {
        Box box = interpolated(entity, tickDelta);
        double eyeY = box.minY + entity.getEyeHeight(entity.getPose());
        Render3D.drawBoxOutline(new Box(box.minX, eyeY - 0.01D, box.minZ,
                box.maxX, eyeY + 0.01D, box.maxZ), eyeColor.getValue(), 1.0F, true);
    }

    private void drawViewingLine(Entity entity, float tickDelta) {
        Vec3d start = entity.getLerpedPos(tickDelta)
                .add(0.0D, entity.getEyeHeight(entity.getPose()), 0.0D);
        Vec3d direction = entity.getRotationVec(tickDelta);
        if (direction.lengthSquared() <= 1.0E-8D) return;
        Render3D.drawLine(start, start.add(direction.multiply(2.0D)),
                viewingLineColor.getValue(), 1.0F, true);
    }

    private Box interpolated(Entity entity, float tickDelta) {
        Box box = entity.getBoundingBox();
        double x = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double z = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());
        return box.offset(x - entity.getX(), y - entity.getY(), z - entity.getZ());
    }
}
