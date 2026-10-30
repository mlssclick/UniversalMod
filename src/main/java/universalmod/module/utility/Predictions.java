package universalmod.module.utility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.WorldRenderEvent;
import universalmod.mixin.accessor.TridentEntityAccessor;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.render.Render3D;
import universalmod.render.animation.Easings;
import universalmod.render.animation.SmoothAnimation;
import universalmod.render.color.ColorUtil;
import universalmod.setting.BooleanSetting;
import universalmod.setting.ColorSetting;
import universalmod.setting.MultiModeSetting;
import universalmod.theme.ThemeColors;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ModuleInformation(moduleName = "Predictions", moduleCategory = ModuleCategory.RENDER)
public final class Predictions extends Module {
    private static final String ARROWS = "Arrows";
    private static final String TRIDENTS = "Tridents";
    private static final String PEARLS = "Ender Pearls";
    private static final String POTIONS = "Potions";
    private static final String THROWABLES = "Throwables";
    private static final int MAX_LIVE_TICKS = 140;
    private static final int MAX_HELD_TICKS = 130;

    private final MultiModeSetting trackedItems = new MultiModeSetting(
            "Tracked Items", "Projectile types rendered by predictions.",
            new String[]{ARROWS, TRIDENTS, PEARLS, POTIONS, THROWABLES},
            ARROWS, TRIDENTS, PEARLS, POTIONS, THROWABLES
    );
    private final BooleanSetting rainbow = new BooleanSetting("Rainbow Color", "Uses a rainbow trajectory color.", false);
    private final BooleanSetting entityHitHighlight = new BooleanSetting("Entity Hit Highlight", "Changes the predicted trajectory color when it will hit an entity.", true);
    private final ColorSetting entityHitColor = new ColorSetting("Entity Hit Color", "Color used when a prediction intersects an entity.", new Color(255, 92, 116, 255));
    private final BooleanSetting potionAreas = new BooleanSetting("Potion Areas", "Shows the predicted effect area for splash and lingering potions.", true);
    private final ColorSetting splashAreaColor = new ColorSetting("Splash Area Color", "Predicted splash-potion effect radius.", new Color(194, 132, 255, 205));
    private final ColorSetting lingeringAreaColor = new ColorSetting("Lingering Area Color", "Predicted lingering-potion cloud radius.", new Color(134, 176, 255, 205));

    private final Map<Integer, SmoothAnimation> trajectoryAnimations = new HashMap<>();
    private final Map<Integer, LivePredictionCache> livePredictionCache = new HashMap<>();
    private final List<Vec3d> pathScratch = new ArrayList<>(MAX_LIVE_TICKS + 2);

    @Override
    public void onDisable() {
        trajectoryAnimations.clear();
        livePredictionCache.clear();
        pathScratch.clear();
        super.onDisable();
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        if (mc.player == null || mc.world == null) {
            trajectoryAnimations.clear();
            livePredictionCache.clear();
            return;
        }

        Set<Integer> activeIds = new HashSet<>();
        double range = Math.max(16.0, mc.options.getViewDistance().getValue() * 16.0);
        double rangeSquared = range * range;

        for (Entity entity : mc.world.getEntities()) {
            LiveProjectile projectile = liveProjectile(entity);
            if (projectile == null || !trackedItems.isEnabled(projectile.settingName)) continue;
            if (!isOwnedByLocalPlayer(entity) || !isMoving(entity) || isReturningTrident(entity)
                    || mc.player.squaredDistanceTo(entity) > rangeSquared) continue;
            renderLiveProjectile(entity, projectile, activeIds);
        }

        renderHeldPrediction(event.getTickDelta());
        fadeMissingTrajectories(activeIds);
    }

    private void renderLiveProjectile(Entity entity, LiveProjectile projectile, Set<Integer> activeIds) {
        int id = entity.getId();
        activeIds.add(id);



        LivePredictionCache cached = livePredictionCache.get(id);
        if (cached == null || cached.tick != entity.age || cached.projectile != projectile) {
            cached = new LivePredictionCache(entity.age, projectile, simulateLive(entity, projectile));
            livePredictionCache.put(id, cached);
        }

        SimulationResult simulation = cached.result;
        if (simulation.path.size() < 2 || simulation.hitLocation == null) return;

        SmoothAnimation animation = trajectoryAnimations.computeIfAbsent(id, ignored -> new SmoothAnimation());
        animate(animation, 1.0);
        float visibility = animation.get();
        int color = simulation.hitEntity != null && entityHitHighlight.getValue()
                ? entityHitColor.getValue()
                : impactColor(entity);
        Direction face = simulation.hitFace != null ? simulation.hitFace : Direction.UP;


        drawPredictionCircle(simulation.hitLocation, face, color, visibility);
    }

    private SimulationResult simulateLive(Entity entity, LiveProjectile projectile) {
        pathScratch.clear();
        Vec3d velocity = entity.getVelocity();
        Vec3d position = entity.getPos();
        pathScratch.add(position);

        boolean throwable = projectile == LiveProjectile.PEARL || projectile == LiveProjectile.POTION || projectile == LiveProjectile.OTHER;
        double gravity = projectile == LiveProjectile.POTION ? 0.05 : throwable ? 0.03 : 0.05;

        for (int tick = 0; tick < MAX_LIVE_TICKS && velocity.lengthSquared() >= 1.0E-6 && insideBuildHeight(position); tick++) {
            double drag = waterDrag(position, throwable);
            if (throwable) velocity = new Vec3d(velocity.x * drag, (velocity.y - gravity) * drag, velocity.z * drag);

            Vec3d next = position.add(velocity);
            BlockHitResult blockHit = raycast(position, next, entity);
            Vec3d segmentEnd = blockHit.getType() == HitResult.Type.MISS ? next : blockHit.getPos();
            Entity entityHit = findEntityHit(entity, position, segmentEnd);

            if (entityHit != null) {
                Box visualBox = entityHit.getBoundingBox();
                Box collisionBox = visualBox.expand(0.3);
                Vec3d collision = collisionBox.raycast(position, segmentEnd).orElse(segmentEnd);
                Direction face = impactFace(collisionBox, collision);
                Vec3d impact = snapToFace(visualBox, collision, face);
                pathScratch.add(impact);
                return new SimulationResult(List.copyOf(pathScratch), impact, face, entityHit);
            }
            if (blockHit.getType() != HitResult.Type.MISS) {
                pathScratch.add(blockHit.getPos());
                return new SimulationResult(List.copyOf(pathScratch), blockHit.getPos(), blockHit.getSide(), null);
            }

            position = next;
            if (!throwable) velocity = new Vec3d(velocity.x * drag, velocity.y * drag - 0.05, velocity.z * drag);
            pathScratch.add(position);
        }
        return new SimulationResult(List.copyOf(pathScratch), null, null, null);
    }

    private void fadeMissingTrajectories(Set<Integer> activeIds) {
        livePredictionCache.keySet().removeIf(id -> !activeIds.contains(id));
        trajectoryAnimations.entrySet().removeIf(entry -> {
            if (activeIds.contains(entry.getKey())) return false;
            SmoothAnimation animation = entry.getValue();
            animate(animation, 0.0);
            return animation.get() <= 0.001F && animation.isFinished();
        });
    }

    private void renderHeldPrediction(float tickDelta) {
        ItemStack mainStack = mc.player.getStackInHand(Hand.MAIN_HAND);
        ItemStack offStack = mc.player.getStackInHand(Hand.OFF_HAND);
        Item main = mainStack.getItem();
        Item off = offStack.getItem();

        float speed = 0.0F;
        boolean throwable = false;
        boolean potionPhysics = false;
        boolean splashPotion = main == Items.SPLASH_POTION || off == Items.SPLASH_POTION;
        boolean lingeringPotion = main == Items.LINGERING_POTION || off == Items.LINGERING_POTION;

        if (main instanceof BowItem && trackedItems.isEnabled(ARROWS)) {
            float pull = bowPull();
            if (pull < 0.1F) return;
            speed = pull * 3.0F;
        } else if (main instanceof CrossbowItem && trackedItems.isEnabled(ARROWS)) {
            speed = 3.0F;
        } else if (main instanceof TridentItem && trackedItems.isEnabled(TRIDENTS)) {
            speed = 2.5F;
        } else if ((main == Items.ENDER_PEARL || off == Items.ENDER_PEARL) && trackedItems.isEnabled(PEARLS)) {
            speed = 1.5F;
            throwable = true;
        } else if ((splashPotion || lingeringPotion) && trackedItems.isEnabled(POTIONS)) {
            speed = 0.5F;
            throwable = true;
            potionPhysics = true;
        } else if (trackedItems.isEnabled(THROWABLES)) {
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
                ? new float[]{-10.0F, 0.0F, 10.0F} : new float[]{0.0F};
        for (float spread : spreads) {
            HeldResult result = simulateHeld(speed, throwable, potionPhysics ? -20.0F : 0.0F,
                    potionPhysics ? 0.05 : 0.03, spread, tickDelta);
            if (result.path.size() < 2 || result.hitLocation == null) continue;

            boolean entityImpact = result.hitEntity != null;
            int color = entityImpact && entityHitHighlight.getValue()
                    ? entityHitColor.getValue() : ColorUtil.rgba(255, 255, 255, 210);
            Direction face = result.hitFace != null ? result.hitFace : Direction.UP;
            if (!(splashPotion || lingeringPotion)) {
                drawPredictionCircle(result.hitLocation, face, color, 1.0F);
            }

            if (potionAreas.getValue() && (splashPotion || lingeringPotion)) {
                if (splashPotion) drawPotionArea(result.hitLocation, face, 4.0, splashAreaColor.getValue());
                else drawPotionArea(result.hitLocation, face, 3.0, lingeringAreaColor.getValue());
            }
        }
    }

    private void drawPotionArea(Vec3d center, Direction face, double radius, int color) {
        int areaColor = hasEntityInPotionArea(center, face, radius)
                ? ColorUtil.rgba(255, 55, 55, 235)
                : withAlpha(color, 230);
        drawLandingCircle(center, face, radius, areaColor);
    }

    private boolean hasEntityInPotionArea(Vec3d center, Direction face, double radius) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;
        double range = radius + 2.0D;
        Box search = new Box(center.x - range, center.y - range, center.z - range,
                center.x + range, center.y + range, center.z + range);
        double radiusSq = radius * radius;
        for (Entity entity : client.world.getOtherEntities(null, search)) {
            if (!entity.isAlive()) continue;
            Vec3d point = entity.getBoundingBox().getCenter();
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

    private void drawPredictionCircle(Vec3d center, Direction face, int baseColor, float visibility) {
        int alpha = MathHelper.clamp(Math.round(((baseColor >>> 24) & 0xFF) * visibility), 0, 255);
        if (alpha <= 0) return;
        int color = withAlpha(baseColor, alpha);
        drawLandingCircle(center, face, 0.48, color);
    }

    private int impactColor(Entity entity) {
        if (!rainbow.getValue()) return ThemeColors.hudAccentColor(230);
        float hue = positiveModulo((entity.getUuid().getLeastSignificantBits() & 0xFFFFL) / 65535.0F
                + (float) ((entity.getX() + entity.getZ()) * 0.05), 1.0F);
        return (Color.HSBtoRGB(hue, 1.0F, 1.0F) & 0x00FFFFFF) | 0xE6000000;
    }

    private HeldResult simulateHeld(float speed, boolean throwable, float pitchOffset, double gravity,
                                    float spreadDegrees, float tickDelta) {
        float pitch = mc.player.getPitch(tickDelta);
        float yaw = mc.player.getYaw(tickDelta);
        double pitchRadians = Math.toRadians(pitch);
        double yawRadians = Math.toRadians(yaw);
        Vec3d look = new Vec3d(-Math.sin(yawRadians) * Math.cos(pitchRadians),
                -Math.sin(Math.toRadians(pitch + pitchOffset)),
                Math.cos(yawRadians) * Math.cos(pitchRadians)).normalize();
        look = rotateAroundLaunchAxis(look, yawRadians, spreadDegrees);

        Vec3d playerVelocity = mc.player.getVelocity();
        Vec3d velocity = look.multiply(speed).add(playerVelocity.x,
                mc.player.isOnGround() ? 0.0 : playerVelocity.y, playerVelocity.z);
        Vec3d position = mc.player.getCameraPosVec(tickDelta);
        List<Vec3d> path = new ArrayList<>(MAX_HELD_TICKS + 2);
        path.add(position);

        for (int tick = 0; tick < MAX_HELD_TICKS && velocity.lengthSquared() >= 1.0E-6 && insideBuildHeight(position); tick++) {
            double drag = waterDrag(position, throwable);
            if (throwable) velocity = new Vec3d(velocity.x * drag, (velocity.y - gravity) * drag, velocity.z * drag);

            Vec3d next = position.add(velocity);
            BlockHitResult blockHit = raycast(position, next, mc.player);
            Vec3d segmentEnd = blockHit.getType() == HitResult.Type.MISS ? next : blockHit.getPos();
            Entity entityHit = findEntityHit(mc.player, position, segmentEnd);

            if (entityHit != null) {
                Box visualBox = entityHit.getBoundingBox();
                Box collisionBox = visualBox.expand(0.3);
                Vec3d collision = collisionBox.raycast(position, segmentEnd).orElse(segmentEnd);
                Direction face = impactFace(collisionBox, collision);
                Vec3d impact = snapToFace(visualBox, collision, face);
                path.add(impact);
                return new HeldResult(path, impact, face, entityHit);
            }
            if (blockHit.getType() != HitResult.Type.MISS) {
                path.add(blockHit.getPos());
                return new HeldResult(path, blockHit.getPos(), blockHit.getSide(), null);
            }

            position = next;
            if (!throwable) velocity = new Vec3d(velocity.x * drag, velocity.y * drag - 0.05, velocity.z * drag);
            path.add(position);
        }
        return new HeldResult(path, null, null, null);
    }

    private Entity findEntityHit(Entity source, Vec3d start, Vec3d end) {
        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;
        Box searchBox = new Box(start, end).expand(1.0);
        for (Entity candidate : mc.world.getOtherEntities(source, searchBox)) {
            if (candidate == mc.player || !(candidate instanceof LivingEntity) || !candidate.isAlive() || candidate.isSpectator()) continue;
            Optional<Vec3d> intersection = candidate.getBoundingBox().expand(0.3).raycast(start, end);
            if (intersection.isEmpty()) continue;
            double distance = start.squaredDistanceTo(intersection.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    private static Direction impactFace(Box box, Vec3d point) {
        double dxMin = Math.abs(point.x - box.minX), dxMax = Math.abs(point.x - box.maxX);
        double dyMin = Math.abs(point.y - box.minY), dyMax = Math.abs(point.y - box.maxY);
        double dzMin = Math.abs(point.z - box.minZ), dzMax = Math.abs(point.z - box.maxZ);
        double best = dxMin;
        Direction face = Direction.WEST;
        if (dxMax < best) { best = dxMax; face = Direction.EAST; }
        if (dyMin < best) { best = dyMin; face = Direction.DOWN; }
        if (dyMax < best) { best = dyMax; face = Direction.UP; }
        if (dzMin < best) { best = dzMin; face = Direction.NORTH; }
        if (dzMax < best) face = Direction.SOUTH;
        return face;
    }

    private static Vec3d snapToFace(Box box, Vec3d point, Direction face) {
        double x = MathHelper.clamp(point.x, box.minX, box.maxX);
        double y = MathHelper.clamp(point.y, box.minY, box.maxY);
        double z = MathHelper.clamp(point.z, box.minZ, box.maxZ);
        return switch (face) {
            case WEST -> new Vec3d(box.minX, y, z);
            case EAST -> new Vec3d(box.maxX, y, z);
            case DOWN -> new Vec3d(x, box.minY, z);
            case UP -> new Vec3d(x, box.maxY, z);
            case NORTH -> new Vec3d(x, y, box.minZ);
            case SOUTH -> new Vec3d(x, y, box.maxZ);
        };
    }

    private void drawLandingCircle(Vec3d center, Direction face, double radius, int color) {
        Direction.Axis axis = face.getAxis();
        Vec3d u = axis == Direction.Axis.Y ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d v = axis == Direction.Axis.X ? new Vec3d(0, 0, 1)
                : axis == Direction.Axis.Z ? new Vec3d(1, 0, 0) : new Vec3d(0, 0, 1);
        double step = Math.PI * 2.0 / 8.0;
        double controlRadius = radius / Math.cos(step * 0.5);
        for (int i = 0; i < 8; i++) {
            Vec3d from = circlePoint(center, u, v, radius, step * i);
            Vec3d to = circlePoint(center, u, v, radius, step * (i + 1));
            Vec3d control = circlePoint(center, u, v, controlRadius, step * (i + 0.5));
            drawQuadraticCurve(from, control, to, color);
        }
    }

    private static void drawQuadraticCurve(Vec3d from, Vec3d control, Vec3d to, int color) {
        Vec3d previous = from;
        for (int segment = 1; segment <= 6; segment++) {
            double t = segment / 6.0, inverse = 1.0 - t;
            Vec3d point = from.multiply(inverse * inverse)
                    .add(control.multiply(2.0 * inverse * t)).add(to.multiply(t * t));
            Render3D.drawLine(previous, point, color, color, 1.5F, false);
            previous = point;
        }
    }

    private static Vec3d circlePoint(Vec3d center, Vec3d u, Vec3d v, double radius, double angle) {
        return center.add(u.multiply(Math.cos(angle) * radius)).add(v.multiply(Math.sin(angle) * radius));
    }

    private Vec3d rotateAroundLaunchAxis(Vec3d look, double yawRadians, float spreadDegrees) {
        if (spreadDegrees == 0.0F) return look;
        Vec3d right = new Vec3d(0, 1, 0).crossProduct(look);
        right = right.lengthSquared() < 1.0E-10 ? new Vec3d(Math.cos(yawRadians), 0, Math.sin(yawRadians)) : right.normalize();
        Vec3d axis = look.crossProduct(right).normalize();
        double radians = Math.toRadians(spreadDegrees), cosine = Math.cos(radians), sine = Math.sin(radians);
        return look.multiply(cosine).add(axis.crossProduct(look).multiply(sine))
                .add(axis.multiply(axis.dotProduct(look) * (1.0 - cosine)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean hasMultishot(ItemStack stack) {
        try {
            Registry<Enchantment> registry = (Registry<Enchantment>) mc.world.getRegistryManager()
                    .getOptional(RegistryKeys.ENCHANTMENT).orElseThrow();
            RegistryEntry<Enchantment> multishot = registry.getEntry(Enchantments.MULTISHOT.getValue()).orElseThrow();
            return EnchantmentHelper.getLevel(multishot, stack) > 0;
        } catch (RuntimeException ignored) { return false; }
    }

    private float bowPull() {
        ItemStack active = mc.player.getActiveItem();
        if (!mc.player.isUsingItem() || !(active.getItem() instanceof BowItem)) return 0.0F;
        return Math.min(BowItem.getPullProgress(mc.player.getItemUseTime()), 1.0F);
    }

    private BlockHitResult raycast(Vec3d start, Vec3d end, Entity source) {
        return mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, source));
    }

    private double waterDrag(Vec3d position, boolean throwable) {
        return mc.world.getFluidState(BlockPos.ofFloored(position)).isIn(FluidTags.WATER) ? throwable ? 0.8 : 0.6 : 0.99;
    }

    private boolean insideBuildHeight(Vec3d position) {
        return position.y >= mc.world.getBottomY() && position.y <= mc.world.getTopYInclusive();
    }

    private boolean isOwnedByLocalPlayer(Entity entity) {
        return entity instanceof ProjectileEntity projectile && projectile.getOwner() == mc.player;
    }

    private static boolean isReturningTrident(Entity entity) {
        return entity instanceof TridentEntity && ((TridentEntityAccessor) entity).universalmod$getReturnTimer() > 0;
    }

    private static boolean isMoving(Entity entity) { return entity.getVelocity().lengthSquared() > 1.0E-6; }

    private LiveProjectile liveProjectile(Entity entity) {
        if (entity instanceof TridentEntity) return LiveProjectile.TRIDENT;
        if (entity instanceof EnderPearlEntity) return LiveProjectile.PEARL;
        if (entity instanceof PotionEntity) return LiveProjectile.POTION;
        if (entity instanceof PersistentProjectileEntity) return LiveProjectile.ARROW;


        if (entity instanceof ProjectileEntity) return LiveProjectile.OTHER;
        return null;
    }

    private static void animate(SmoothAnimation animation, double target) {
        animation.run(target, 0.25, Easings.BACK_OUT, true);
        animation.update();
    }

    private static float positiveModulo(float value, float divisor) { return (value % divisor + divisor) % divisor; }
    private static int withAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | (MathHelper.clamp(alpha, 0, 255) << 24); }

    private enum LiveProjectile {
        ARROW(ARROWS), TRIDENT(TRIDENTS), PEARL(PEARLS), POTION(POTIONS), OTHER(THROWABLES);
        private final String settingName;
        LiveProjectile(String settingName) { this.settingName = settingName; }
    }

    private static final class LivePredictionCache {
        final int tick;
        final LiveProjectile projectile;
        final SimulationResult result;
        LivePredictionCache(int tick, LiveProjectile projectile, SimulationResult result) {
            this.tick = tick; this.projectile = projectile; this.result = result;
        }
    }

    private static final class SimulationResult {
        final List<Vec3d> path;
        final Vec3d hitLocation;
        final Direction hitFace;
        final Entity hitEntity;
        SimulationResult(List<Vec3d> path, Vec3d hitLocation, Direction hitFace, Entity hitEntity) {
            this.path = path; this.hitLocation = hitLocation; this.hitFace = hitFace; this.hitEntity = hitEntity;
        }
    }

    private static final class HeldResult {
        final List<Vec3d> path;
        final Vec3d hitLocation;
        final Direction hitFace;
        final Entity hitEntity;
        HeldResult(List<Vec3d> path, Vec3d hitLocation, Direction hitFace, Entity hitEntity) {
            this.path = path; this.hitLocation = hitLocation; this.hitFace = hitFace; this.hitEntity = hitEntity;
        }
    }
}
