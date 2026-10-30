package universalmod.module.utility;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.MultiModeSetting;
import universalmod.mixin.accessor.ThrownTridentAccessor;
import universalmod.render.Render3D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.theme.ThemeColors;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Predictions extends Module {
    private static final String ARROWS = "Arrows";
    private static final String TRIDENTS = "Tridents";
    private static final String PEARLS = "Ender Pearls";
    private static final String POTIONS = "Potions";
    private static final String THROWABLES = "Throwables";
    private static final int MAX_LIVE_TICKS = 140;
    private static final int MAX_HELD_TICKS = 130;

    private final MultiModeSetting trackedItems = register(new MultiModeSetting(
            "Tracked Items", "Projectile types rendered by predictions.",
            new String[]{ARROWS, TRIDENTS, PEARLS, POTIONS, THROWABLES}, ARROWS, TRIDENTS, PEARLS, POTIONS, THROWABLES
    ));
    private final BooleanSetting rainbow = register(new BooleanSetting(
            "Rainbow Color", "Uses a rainbow trajectory color.", false
    ));
    private final BooleanSetting entityHitHighlight = register(new BooleanSetting(
            "Entity Hit Highlight", "Changes the predicted trajectory color when it will hit an entity.", true
    ));
    private final ColorSetting entityHitColor = register(new ColorSetting(
            "Entity Hit Color", "Color used when a prediction intersects an entity.", new Color(255, 92, 116, 255)
    ));
    private final BooleanSetting potionAreas = register(new BooleanSetting(
            "Potion Areas", "Shows the predicted effect area for splash and lingering potions.", true
    ));
    private final ColorSetting splashAreaColor = register(new ColorSetting(
            "Splash Area Color", "Predicted splash-potion effect radius.", new Color(194, 132, 255, 205)
    ));
    private final ColorSetting lingeringAreaColor = register(new ColorSetting(
            "Lingering Area Color", "Predicted lingering-potion cloud radius.", new Color(134, 176, 255, 205)
    ));

    private final Map<Integer, SmoothAnimation> trajectoryAnimations = new HashMap<>();
    private final Map<Integer, LivePredictionCache> livePredictionCache = new HashMap<>();
    private final List<Vec3> pathScratch = new ArrayList<>(MAX_LIVE_TICKS + 2);

    public Predictions() {
        super("predictions", "Predictions", ModuleCategory.RENDER);
    }

    @Override
    protected void onDisable() {
        trajectoryAnimations.clear();
        livePredictionCache.clear();
        pathScratch.clear();
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        if (mc.player == null || mc.level == null) {
            trajectoryAnimations.clear();
            livePredictionCache.clear();
            return;
        }

        Set<Integer> activeIds = new HashSet<>();
        double range = Math.max(16.0D, mc.options.renderDistance().get() * 16.0D);
        double rangeSqr = range * range;
        for (Entity entity : mc.level.entitiesForRendering()) {
            LiveProjectile projectile = liveProjectile(entity);
            if (projectile == null || !trackedItems.isSelected(projectile.settingName())) {
                continue;
            }
            if (!isOwnedByLocalPlayer(entity) || !isMoving(entity) || isReturningTrident(entity)
                    || mc.player.distanceToSqr(entity) > rangeSqr) {
                continue;
            }
            renderLiveProjectile(entity, projectile, activeIds, event.getPartialTicks());
        }

        renderHeldPrediction(event);
        fadeMissingTrajectories(activeIds);
    }

    private void renderLiveProjectile(Entity entity, LiveProjectile projectile, Set<Integer> activeIds, float partialTick) {
        int id = entity.getId();
        activeIds.add(id);


        LivePredictionCache cached = livePredictionCache.get(id);
        if (cached == null || cached.tick() != entity.tickCount || cached.projectile() != projectile) {
            cached = new LivePredictionCache(entity.tickCount, projectile, simulateLive(entity, projectile));
            livePredictionCache.put(id, cached);
        }

        SimulationResult simulation = cached.result();
        if (simulation.path().size() < 2 || simulation.hitLocation() == null) {
            return;
        }

        SmoothAnimation animation = trajectoryAnimations.computeIfAbsent(id, ignored -> new SmoothAnimation());
        animate(animation, 1.0D);
        float progress = animation.get();
        int color = simulation.hitEntity() != null && entityHitHighlight.getValue()
                ? entityHitColor.getValue().getRGB()
                : impactColor(entity);
        Direction face = simulation.hitFace() != null ? simulation.hitFace() : Direction.UP;


        drawPredictionCircle(simulation.hitLocation(), face, color, progress);
    }

    private SimulationResult simulateLive(Entity entity, LiveProjectile projectile) {
        pathScratch.clear();
        Vec3 velocity = entity.getDeltaMovement();


        Vec3 position = entity.position();
        pathScratch.add(position);
        boolean throwable = projectile == LiveProjectile.PEARL || projectile == LiveProjectile.POTION || projectile == LiveProjectile.OTHER;
        double gravity = projectile == LiveProjectile.POTION ? 0.05D : throwable ? 0.03D : 0.05D;
        for (int tick = 0; tick < MAX_LIVE_TICKS && velocity.lengthSqr() >= 1.0E-6D && insideBuildHeight(position); tick++) {
            double drag = waterDrag(position, throwable);
            if (throwable) {
                velocity = new Vec3(velocity.x * drag, (velocity.y - gravity) * drag, velocity.z * drag);
            }
            Vec3 next = position.add(velocity);
            BlockHitResult blockHit = clip(position, next, entity);
            Vec3 segmentEnd = blockHit.getType() == HitResult.Type.MISS ? next : blockHit.getLocation();
            Entity entityHit = findEntityHit(entity, position, segmentEnd);
            if (entityHit != null) {
                AABB entityBox = entityHit.getBoundingBox();
                AABB collisionBox = entityBox.inflate(0.3D);
                Vec3 collision = collisionBox.clip(position, segmentEnd).orElse(segmentEnd);


                Direction face = impactFace(collisionBox, collision);
                Vec3 impact = snapToFace(entityBox, collision, face);
                pathScratch.add(impact);
                return new SimulationResult(List.copyOf(pathScratch), impact, face, entityHit);
            }
            if (blockHit.getType() != HitResult.Type.MISS) {
                pathScratch.add(blockHit.getLocation());
                return new SimulationResult(List.copyOf(pathScratch), blockHit.getLocation(), blockHit.getDirection(), null);
            }
            position = next;
            if (!throwable) {
                velocity = new Vec3(velocity.x * drag, velocity.y * drag - 0.05D, velocity.z * drag);
            }
            pathScratch.add(position);
        }
        return new SimulationResult(List.copyOf(pathScratch), null, null, null);
    }

    private void fadeMissingTrajectories(Set<Integer> activeIds) {
        livePredictionCache.keySet().removeIf(id -> !activeIds.contains(id));
        trajectoryAnimations.entrySet().removeIf(entry -> {
            if (activeIds.contains(entry.getKey())) {
                return false;
            }
            SmoothAnimation animation = entry.getValue();
            animate(animation, 0.0D);
            return animation.get() <= 0.001F && animation.isFinished();
        });
    }

    private void renderHeldPrediction(WorldRenderEvent event) {
        ItemStack mainStack = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offStack = mc.player.getItemInHand(InteractionHand.OFF_HAND);
        Item main = mainStack.getItem();
        Item off = offStack.getItem();

        float speed = 0.0F;
        boolean throwable = false;
        boolean potionPhysics = false;
        boolean splashPotion = main == Items.SPLASH_POTION || off == Items.SPLASH_POTION;
        boolean lingeringPotion = main == Items.LINGERING_POTION || off == Items.LINGERING_POTION;

        if (main instanceof BowItem && trackedItems.isSelected(ARROWS)) {
            float pull = bowPull();
            if (pull < 0.1F) return;
            speed = pull * 3.0F;
        } else if (main instanceof CrossbowItem && trackedItems.isSelected(ARROWS)) {
            speed = 3.0F;
        } else if (main instanceof TridentItem && trackedItems.isSelected(TRIDENTS)) {
            speed = 2.5F;
        } else if ((main == Items.ENDER_PEARL || off == Items.ENDER_PEARL) && trackedItems.isSelected(PEARLS)) {
            speed = 1.5F;
            throwable = true;
        } else if ((splashPotion || lingeringPotion) && trackedItems.isSelected(POTIONS)) {
            speed = 0.5F;
            throwable = true;
            potionPhysics = true;
        } else if (trackedItems.isSelected(THROWABLES)) {
            if (main == Items.SNOWBALL || off == Items.SNOWBALL || main == Items.EGG || off == Items.EGG) {
                speed = 1.5F;
                throwable = true;
            } else if (main == Items.EXPERIENCE_BOTTLE || off == Items.EXPERIENCE_BOTTLE) {
                speed = 0.7F;
                throwable = true;
                potionPhysics = true;
            } else if (main == Items.WIND_CHARGE || off == Items.WIND_CHARGE) {
                speed = 1.5F;
                throwable = true;
            }
        }

        if (speed == 0.0F) return;

        float[] spreads = main instanceof CrossbowItem && hasMultishot(mainStack)
                ? new float[]{-10.0F, 0.0F, 10.0F}
                : new float[]{0.0F};
        for (float spread : spreads) {
            HeldResult result = simulateHeld(
                    speed, throwable, potionPhysics ? -20.0F : 0.0F,
                    potionPhysics ? 0.05D : 0.03D, spread, event.getPartialTicks()
            );
            if (result.path().size() < 2 || result.hitLocation() == null) continue;

            boolean entityImpact = result.hitEntity() != null;
            int color = entityImpact && entityHitHighlight.getValue()
                    ? entityHitColor.getValue().getRGB()
                    : ColorUtil.rgba(255, 255, 255, 210);
            Direction face = result.hitFace() != null ? result.hitFace() : Direction.UP;


            if (!(splashPotion || lingeringPotion)) {
                drawPredictionCircle(result.hitLocation(), face, color, 1.0F);
            }

            if (potionAreas.getValue() && (splashPotion || lingeringPotion)) {
                if (splashPotion) {
                    drawPotionArea(result.hitLocation(), face, 4.0D, splashAreaColor.getValue().getRGB());
                } else {
                    drawPotionArea(result.hitLocation(), face, 3.0D, lingeringAreaColor.getValue().getRGB());
                }
            }
        }
    }

    private void drawPotionArea(Vec3 center, Direction face, double radius, int color) {
        int areaColor = hasEntityInPotionArea(center, face, radius)
                ? ColorUtil.rgba(255, 55, 55, 235)
                : withAlpha(color, 230);
        drawLandingCircle(center, face, radius, areaColor);
    }

    private boolean hasEntityInPotionArea(Vec3 center, Direction face, double radius) {
        if (mc.level == null) return false;
        double range = radius + 2.0D;
        AABB search = new AABB(center.x - range, center.y - range, center.z - range,
                center.x + range, center.y + range, center.z + range);
        double radiusSq = radius * radius;
        for (Entity entity : mc.level.getEntities((Entity) null, search)) {
            if (!entity.isAlive() || entity == mc.player) continue;
            Vec3 point = entity.getBoundingBox().getCenter();
            double dx = point.x - center.x;
            double dy = point.y - center.y;
            double dz = point.z - center.z;
            Direction.Axis axis = face.getAxis();
            double planeSq = axis == Direction.Axis.X ? dy * dy + dz * dz
                    : axis == Direction.Axis.Y ? dx * dx + dz * dz
                    : dx * dx + dy * dy;
            double perpendicular = axis == Direction.Axis.X ? Math.abs(dx)
                    : axis == Direction.Axis.Y ? Math.abs(dy) : Math.abs(dz);
            if (planeSq <= radiusSq && perpendicular <= 2.0D) return true;
        }
        return false;
    }

    private void drawPredictionCircle(Vec3 center, Direction face, int baseColor, float visibility) {
        float v = Mth.clamp(visibility, 0.0F, 1.0F);
        if (v <= 0.01F) return;
        drawLandingCircle(center, face, 0.33D, withAlpha(baseColor, Math.round(210.0F * v)));
    }

    private int impactColor(Entity entity) {
        if (!rainbow.getValue()) return ThemeColors.hudAccentColor(255);
        float hue = positiveModulo((entity.getUUID().getLeastSignificantBits() & 0xFFFFL) / 65535.0F
                + (float) ((entity.getX() + entity.getZ()) * 0.04D), 1.0F);
        return 0xFF000000 | (Color.HSBtoRGB(hue, 0.88F, 1.0F) & 0x00FFFFFF);
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.clamp(alpha, 0, 255) << 24;
    }

    private HeldResult simulateHeld(float speed, boolean throwable, float pitchOffset, double gravity, float spreadDegrees, float partialTick) {
        float pitch = mc.player.getViewXRot(partialTick);
        float yaw = mc.player.getViewYRot(partialTick);
        double pitchRad = Math.toRadians(pitch);
        double yawRad = Math.toRadians(yaw);
        Vec3 look = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(Math.toRadians(pitch + pitchOffset)),
                Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
        look = rotateAroundLaunchAxis(look, yawRad, spreadDegrees);
        Vec3 playerVelocity = mc.player.getDeltaMovement();
        Vec3 velocity = look.scale(speed).add(playerVelocity.x, mc.player.onGround() ? 0.0D : playerVelocity.y, playerVelocity.z);
        Vec3 position = mc.player.getEyePosition(partialTick);
        List<Vec3> path = new ArrayList<>(MAX_HELD_TICKS + 2);
        path.add(position);
        for (int tick = 0; tick < MAX_HELD_TICKS && velocity.lengthSqr() >= 1.0E-6D && insideBuildHeight(position); tick++) {
            double drag = waterDrag(position, throwable);
            if (throwable) {
                velocity = new Vec3(velocity.x * drag, (velocity.y - gravity) * drag, velocity.z * drag);
            }
            Vec3 next = position.add(velocity);
            BlockHitResult blockHit = clip(position, next, mc.player);
            Vec3 segmentEnd = blockHit.getType() == HitResult.Type.MISS ? next : blockHit.getLocation();
            Entity entityHit = findEntityHit(position, segmentEnd);
            if (entityHit != null) {
                AABB entityBox = entityHit.getBoundingBox();
                AABB collisionBox = entityBox.inflate(0.3D);
                Vec3 collision = collisionBox.clip(position, segmentEnd).orElse(segmentEnd);


                Direction face = impactFace(collisionBox, collision);
                Vec3 impact = snapToFace(entityBox, collision, face);
                path.add(impact);
                return new HeldResult(path, impact, face, entityHit);
            }
            if (blockHit.getType() != HitResult.Type.MISS) {
                path.add(blockHit.getLocation());
                return new HeldResult(path, blockHit.getLocation(), blockHit.getDirection(), null);
            }
            position = next;
            if (!throwable) {
                velocity = new Vec3(velocity.x * drag, velocity.y * drag - 0.05D, velocity.z * drag);
            }
            path.add(position);
        }
        return new HeldResult(path, null, null, null);
    }

    private Entity findEntityHit(Vec3 start, Vec3 end) {
        return findEntityHit(mc.player, start, end);
    }

    private Entity findEntityHit(Entity source, Vec3 start, Vec3 end) {
        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity candidate : mc.level.getEntities(source, new AABB(start, end).inflate(1.0D))) {
            if (candidate == mc.player || !(candidate instanceof LivingEntity) || !candidate.isAlive() || candidate.isSpectator()) continue;
            Optional<Vec3> intersection = candidate.getBoundingBox().inflate(0.3D).clip(start, end);
            if (intersection.isPresent()) {
                double distance = start.distanceToSqr(intersection.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = candidate;
                }
            }
        }
        return closest;
    }


    private static Direction impactFace(AABB box, Vec3 point) {
        double dxMin = Math.abs(point.x - box.minX);
        double dxMax = Math.abs(point.x - box.maxX);
        double dyMin = Math.abs(point.y - box.minY);
        double dyMax = Math.abs(point.y - box.maxY);
        double dzMin = Math.abs(point.z - box.minZ);
        double dzMax = Math.abs(point.z - box.maxZ);
        double best = dxMin;
        Direction face = Direction.WEST;
        if (dxMax < best) { best = dxMax; face = Direction.EAST; }
        if (dyMin < best) { best = dyMin; face = Direction.DOWN; }
        if (dyMax < best) { best = dyMax; face = Direction.UP; }
        if (dzMin < best) { best = dzMin; face = Direction.NORTH; }
        if (dzMax < best) { face = Direction.SOUTH; }
        return face;
    }

    private static Vec3 snapToFace(AABB box, Vec3 point, Direction face) {
        double x = Mth.clamp(point.x, box.minX, box.maxX);
        double y = Mth.clamp(point.y, box.minY, box.maxY);
        double z = Mth.clamp(point.z, box.minZ, box.maxZ);
        return switch (face) {
            case WEST -> new Vec3(box.minX, y, z);
            case EAST -> new Vec3(box.maxX, y, z);
            case DOWN -> new Vec3(x, box.minY, z);
            case UP -> new Vec3(x, box.maxY, z);
            case NORTH -> new Vec3(x, y, box.minZ);
            case SOUTH -> new Vec3(x, y, box.maxZ);
        };
    }

    private void drawLandingCircle(Vec3 center, Direction face, double radius, int color) {
        Direction.Axis axis = face.getAxis();
        Vec3 u = axis == Direction.Axis.Y ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 v = axis == Direction.Axis.X ? new Vec3(0.0D, 0.0D, 1.0D)
                : axis == Direction.Axis.Z ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, 1.0D);
        double step = Math.PI * 2.0D / 8.0D;
        double controlRadius = radius / Math.cos(step * 0.5D);
        for (int i = 0; i < 8; i++) {
            Vec3 from = circlePoint(center, u, v, radius, step * i);
            Vec3 to = circlePoint(center, u, v, radius, step * (i + 1));
            Vec3 control = circlePoint(center, u, v, controlRadius, step * (i + 0.5D));
            drawQuadraticCurve(from, control, to, color);
        }
    }

    private static void drawQuadraticCurve(Vec3 from, Vec3 control, Vec3 to, int color) {
        Vec3 previous = from;
        for (int segment = 1; segment <= 6; segment++) {
            double t = segment / 6.0D;
            double inverse = 1.0D - t;
            Vec3 point = from.scale(inverse * inverse)
                    .add(control.scale(2.0D * inverse * t))
                    .add(to.scale(t * t));
            Render3D.drawLineGradient(previous, point, color, color, 1.5F, true);
            previous = point;
        }
    }

    private static Vec3 circlePoint(Vec3 center, Vec3 u, Vec3 v, double radius, double angle) {
        return center.add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
    }

    private Vec3 rotateAroundLaunchAxis(Vec3 look, double yawRad, float spreadDegrees) {
        if (spreadDegrees == 0.0F) {
            return look;
        }
        Vec3 right = new Vec3(0.0D, 1.0D, 0.0D).cross(look);
        right = right.lengthSqr() < 1.0E-10D
                ? new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad))
                : right.normalize();
        Vec3 axis = look.cross(right).normalize();
        double radians = Math.toRadians(spreadDegrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return look.scale(cosine).add(axis.cross(look).scale(sine)).add(axis.scale(axis.dot(look) * (1.0D - cosine)));
    }

    private boolean hasMultishot(ItemStack stack) {
        try {
            var registry = mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            var enchantment = registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MULTISHOT);
            return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) > 0;
        } catch (RuntimeException registryFailure) {
            return false;
        }
    }

    private float bowPull() {
        ItemStack active = mc.player.getUseItem();
        if (!mc.player.isUsingItem() || !(active.getItem() instanceof BowItem)) {
            return 0.0F;
        }
        return Math.min(BowItem.getPowerForTime(mc.player.getTicksUsingItem()), 1.0F);
    }

    private BlockHitResult clip(Vec3 start, Vec3 end, Entity source) {
        return mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
    }

    private double waterDrag(Vec3 position, boolean throwable) {
        return mc.level.getFluidState(BlockPos.containing(position)).is(FluidTags.WATER) ? throwable ? 0.8D : 0.6D : 0.99D;
    }

    private boolean insideBuildHeight(Vec3 position) {
        return position.y >= mc.level.getMinY() && position.y <= mc.level.getMaxY();
    }

    private boolean isOwnedByLocalPlayer(Entity entity) {
        return entity instanceof Projectile projectile && projectile.getOwner() == mc.player;
    }

    private static boolean isReturningTrident(Entity entity) {
        return entity instanceof ThrownTrident
                && ((ThrownTridentAccessor) entity).universalmod$getClientSideReturnTickCount() > 0;
    }

    private static boolean isMoving(Entity entity) {
        return entity.getDeltaMovement().lengthSqr() > 1.0E-6D;
    }

    private LiveProjectile liveProjectile(Entity entity) {
        if (entity instanceof ThrownTrident) return LiveProjectile.TRIDENT;
        if (entity instanceof ThrownEnderpearl) return LiveProjectile.PEARL;
        if (entity instanceof ThrownSplashPotion) return LiveProjectile.POTION;
        if (entity instanceof AbstractArrow) return LiveProjectile.ARROW;


        if (entity instanceof Projectile) return LiveProjectile.OTHER;
        return null;
    }

    private static void animate(SmoothAnimation animation, double target) {
        if (Math.abs(animation.getToValue() - target) > 1.0E-6D) {
            animation.run(target, 0.25D, Easings.BACK_OUT);
        }
        animation.update();
    }

    private static float positiveModulo(float value, float divisor) {
        return (value % divisor + divisor) % divisor;
    }

    private enum LiveProjectile {
        ARROW(ARROWS), TRIDENT(TRIDENTS), PEARL(PEARLS), POTION(POTIONS), OTHER(THROWABLES);

        private final String settingName;

        LiveProjectile(String settingName) {
            this.settingName = settingName;
        }

        private String settingName() {
            return settingName;
        }

    }

    private record HeldResult(List<Vec3> path, Vec3 hitLocation, Direction hitFace, Entity hitEntity) {}
    private record LivePredictionCache(int tick, LiveProjectile projectile, SimulationResult result) {}
    private record SimulationResult(List<Vec3> path, Vec3 hitLocation, Direction hitFace, Entity hitEntity) {}
}
