package universalmod.module.render.richdog;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class FlyPetBrain {
    public static final double MAX_DISTANCE = 30.0;
    private static final double RETURN_DISTANCE = 26.0;
    private static final double MAX_RENDER_DISTANCE = 29.75;
    private static final double MAX_SPEED = 0.48;
    private static final double BODY_RADIUS = 0.34;
    private static final int ACTIONS = 9;
    private static final int SENSOR_STATES = 128;
    private static final float[][] ACTION_STEERING = {
            {0.0F, 0.0F}, {-1.0F, 0.0F}, {1.0F, 0.0F},
            {0.0F, -1.0F}, {0.0F, 1.0F}, {-1.0F, -1.0F},
            {1.0F, -1.0F}, {-1.0F, 1.0F}, {1.0F, 1.0F}
    };
    private static final Vec3[] BODY_PROBES = {
            new Vec3(0.0, 0.0, 0.0),
            new Vec3(BODY_RADIUS, 0.0, 0.0), new Vec3(-BODY_RADIUS, 0.0, 0.0),
            new Vec3(0.0, BODY_RADIUS, 0.0), new Vec3(0.0, -BODY_RADIUS, 0.0),
            new Vec3(0.0, 0.0, BODY_RADIUS), new Vec3(0.0, 0.0, -BODY_RADIUS)
    };

    public enum State {
        HOVER, EXPLORE, ORBIT, CHASE, AVOID, RETURN, ESCAPE, REST
    }

    public record Statistics(State state, double distance, double speed,
                             float wingFrequency, float stamina, int decisions,
                             int obstacleAvoidances, int returns, int flightTicks,
                             double targetDistance, boolean chasing, boolean obstacleAhead) {
    }

    private record Sensors(boolean frontBlocked, boolean leftBlocked, boolean rightBlocked,
                           boolean upBlocked, boolean groundClose, double groundDistance,
                           float novelty, float energy, float light, double ownerDistance) {
        int key() {
            int key = (frontBlocked ? 1 : 0) | (leftBlocked ? 2 : 0)
                    | (rightBlocked ? 4 : 0) | (upBlocked ? 8 : 0)
                    | (groundClose ? 16 : 0) | (ownerDistance > 19.0 ? 32 : 0)
                    | (energy < 0.28F ? 64 : 0);
            return key;
        }
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final Map<Long, Integer> exploredCells = new HashMap<>();
    private final float[][] learnedActions = new float[SENSOR_STATES][ACTIONS];

    private Player owner;
    private LivingEntity target;
    private Object world;
    private Vec3 position;
    private Vec3 previousPosition;
    private Vec3 velocity = Vec3.ZERO;
    private Vec3 destination = Vec3.ZERO;
    private Vec3 previousDecisionPosition = Vec3.ZERO;
    private State state = State.EXPLORE;
    private int ticks;
    private int stateTicks;
    private int destinationTicks;
    private int actionTicks;
    private int currentAction;
    private int previousSensorKey;
    private int decisions;
    private int obstacleAvoidances;
    private int returns;
    private int flightTicks;
    private int stuckTicks;
    private int escapeTicks;
    private int restTicks;
    private float headingYaw;
    private float headingPitch;
    private float bodyYaw;
    private float bodyPitch;
    private float bodyRoll;
    private float headYaw;
    private float headPitch;
    private float wingPhase;
    private float previousWingPhase;
    private float legPhase;
    private float previousLegPhase;
    private float legAmount;
    private float stamina = 1.0F;
    private float dopamine;
    private float wingFrequency = 8.2F;
    private boolean obstacleAhead;
    private double ownerDistance;
    private double targetDistance = -1.0;
    private double previousGoalDistance;

    public void setOwner(Player owner) {
        if (owner != this.owner || (owner != null && owner.level() != world)) {
            reset();
            world = owner == null ? null : owner.level();
        }
        this.owner = owner;
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    public void reset() {
        owner = null;
        target = null;
        world = null;
        position = null;
        previousPosition = null;
        velocity = Vec3.ZERO;
        destination = Vec3.ZERO;
        exploredCells.clear();
        for (float[] actions : learnedActions) {
            java.util.Arrays.fill(actions, 0.0F);
        }
        state = State.EXPLORE;
        ticks = stateTicks = destinationTicks = actionTicks = currentAction = previousSensorKey = 0;
        decisions = obstacleAvoidances = returns = flightTicks = stuckTicks = escapeTicks = restTicks = 0;
        headingYaw = headingPitch = bodyYaw = bodyPitch = bodyRoll = headYaw = headPitch = 0.0F;
        wingPhase = previousWingPhase = legPhase = previousLegPhase = legAmount = dopamine = 0.0F;
        stamina = 1.0F;
        wingFrequency = 8.2F;
        obstacleAhead = false;
        ownerDistance = previousGoalDistance = 0.0;
        targetDistance = -1.0;
    }

    public void tick() {
        if (owner == null || mc.level == null || owner.level() != mc.level) {
            return;
        }
        Vec3 anchor = owner.position().add(0.0, 1.65, 0.0);
        if (position == null || !finite(position) || position.distanceToSqr(owner.position()) > MAX_DISTANCE * MAX_DISTANCE) {
            spawnNearOwner(anchor);
        }
        previousPosition = position;
        ticks++;
        flightTicks++;
        stateTicks++;
        ownerDistance = position.distanceTo(owner.position());
        LivingEntity chase = validTarget() && target.position().distanceToSqr(owner.position()) < 25.0 * 25.0
                ? target : null;
        targetDistance = chase == null ? -1.0 : position.distanceTo(chase.position());
        Sensors sensors = readSensors();
        obstacleAhead = sensors.frontBlocked;
        recordVisit(position);

        if (ownerDistance >= RETURN_DISTANCE) {
            changeState(State.RETURN);
        } else if (chase != null) {
            changeState(State.CHASE);
        } else if (escapeTicks > 0) {
            changeState(State.ESCAPE);
        } else if (restTicks > 0) {
            changeState(State.REST);
        } else {
            if (destinationTicks <= 0 || position.distanceToSqr(destination) < 2.6 * 2.6
                    || stuckTicks > 18 || !finite(destination)) {
                pickNewDestination(anchor);
            }
            changeState(sensors.frontBlocked ? State.AVOID : State.EXPLORE);
        }

        Vec3 goal = switch (state) {
            case RETURN -> anchor;
            case CHASE -> chase.position().add(0.0, 1.25, 0.0);
            case ESCAPE, EXPLORE, AVOID -> destination;
            case REST -> anchor.add(0.0, 0.55, 0.0);
            case ORBIT, HOVER -> destination;
        };
        if (goal.distanceToSqr(owner.position()) > RETURN_DISTANCE * RETURN_DISTANCE) {
            goal = owner.position().add(goal.subtract(owner.position()).normalize().scale(RETURN_DISTANCE));
        }

        Vec3 towardGoal = goal.subtract(position);
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-towardGoal.x, towardGoal.z));
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(towardGoal.y,
                Math.max(0.001, Math.sqrt(towardGoal.x * towardGoal.x + towardGoal.z * towardGoal.z))));
        if (actionTicks <= 0 || sensors.frontBlocked || ownerDistance >= RETURN_DISTANCE) {
            updateLearning(sensors, goal);
            previousSensorKey = sensors.key();
            currentAction = selectAction(sensors, desiredYaw, desiredPitch);
            actionTicks = 4 + ThreadLocalRandom.current().nextInt(5);
            previousDecisionPosition = position;
            previousGoalDistance = position.distanceTo(goal);
            decisions++;
        }
        actionTicks--;

        float yawError = Mth.wrapDegrees(desiredYaw - headingYaw);
        float pitchError = desiredPitch - headingPitch;
        float[] action = ACTION_STEERING[currentAction];
        float turn = Mth.clamp(yawError * 0.18F + action[0] * 5.5F, -15.0F, 15.0F);
        float pitchTurn = Mth.clamp(pitchError * 0.14F + action[1] * 3.7F, -9.0F, 9.0F);
        if (sensors.frontBlocked) {
            turn += sensors.leftBlocked && !sensors.rightBlocked ? 12.0F
                    : sensors.rightBlocked && !sensors.leftBlocked ? -12.0F
                    : action[0] >= 0.0F ? 9.0F : -9.0F;
            if (!sensors.upBlocked) {
                pitchTurn -= 5.0F;
            }
        }
        if (sensors.groundClose && !sensors.upBlocked) {
            pitchTurn -= 4.0F;
        }
        headingYaw = Mth.wrapDegrees(headingYaw + Mth.clamp(turn, -19.0F, 19.0F));
        headingPitch = Mth.clamp(headingPitch + pitchTurn, -55.0F, 55.0F);
        Vec3 heading = forward(headingYaw, headingPitch);
        Vec3 desiredVelocity = heading.scale(state == State.RETURN ? 0.47
                : state == State.CHASE ? 0.45 : state == State.REST ? 0.12 : 0.37);
        Vec3 avoidance = obstacleRepulsion(heading);
        if (avoidance.lengthSqr() > 1.0E-5) {
            desiredVelocity = desiredVelocity.add(avoidance.scale(0.20));
        }
        if (ownerDistance > 22.0) {
            desiredVelocity = desiredVelocity.add(owner.position().subtract(position).normalize()
                    .scale((ownerDistance - 22.0) * 0.055));
        }
        velocity = velocity.scale(0.77).add(desiredVelocity.scale(0.23));
        if (velocity.lengthSqr() > MAX_SPEED * MAX_SPEED) {
            velocity = velocity.normalize().scale(MAX_SPEED);
        }
        Vec3 next = moveWithCollision(position, velocity);
        if (next.distanceToSqr(position) < 0.0004 && state != State.REST) {
            stuckTicks++;
        } else {
            stuckTicks = Math.max(0, stuckTicks - 2);
        }
        if (stuckTicks > 10) {
            escapeTicks = Math.max(escapeTicks, 20);
            if (escapeTicks == 20) {
                obstacleAvoidances++;
            }
        }
        if (stuckTicks > 35) {
            destinationTicks = 0;
            escapeTicks = 20;
            stuckTicks = 0;
        }
        if (escapeTicks > 0) {
            escapeTicks--;
        }
        if (restTicks > 0) {
            restTicks--;
        }
        if (destinationTicks > 0) {
            destinationTicks--;
        }

        Vec3 relative = next.subtract(owner.position());
        if (relative.lengthSqr() > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
            next = owner.position().add(relative.normalize().scale(MAX_RENDER_DISTANCE));
            velocity = next.subtract(position);
            changeState(State.RETURN);
        }
        position = next;
        ownerDistance = position.distanceTo(owner.position());
        if (state == State.REST) {
            stamina = Math.min(1.0F, stamina + 0.010F);
        } else {
            stamina = Math.max(0.15F, stamina - 0.0006F);
        }
        if (stamina < 0.23F && restTicks == 0 && ownerDistance < 20.0) {
            restTicks = 30 + ThreadLocalRandom.current().nextInt(50);
        }
        wingFrequency = Mth.lerp(0.17F, wingFrequency,
                Mth.clamp((float) (7.5 + velocity.length() * 6.5 + dopamine * 1.1), 6.0F, 11.4F));
        previousWingPhase = wingPhase;
        previousLegPhase = legPhase;
        wingPhase += wingFrequency * (Mth.PI * 2.0F) / 20.0F;
        legPhase += (float) (0.32 + velocity.length() * 9.5);
        legAmount = Mth.lerp(0.25F, legAmount,
                Mth.clamp((float) (0.40 + velocity.length() * 2.3), 0.4F, 1.0F));
        float newYaw = (float) Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - 90.0F;
        float yawChange = velocity.horizontalDistanceSqr() > 1.0E-5
                ? Mth.wrapDegrees(newYaw - bodyYaw) : 0.0F;
        bodyYaw = Mth.wrapDegrees(bodyYaw + yawChange * 0.28F);
        bodyPitch = Mth.lerp(0.17F, bodyPitch, Mth.clamp((float) (-velocity.y * 52.0), -25.0F, 25.0F));
        bodyRoll = Mth.lerp(0.17F, bodyRoll, Mth.clamp(-yawChange * 0.23F, -26.0F, 26.0F));
        float faceYaw = (float) Math.toDegrees(Math.atan2(owner.getZ() - position.z,
                owner.getX() - position.x)) - 90.0F;
        headYaw = Mth.lerp(0.2F, headYaw, Mth.clamp(Mth.wrapDegrees(faceYaw - bodyYaw), -53.0F, 53.0F));
        headPitch = Mth.lerp(0.2F, headPitch, (float) Mth.clamp(-velocity.y * 95.0, -33.0, 33.0));
        dopamine *= 0.98F;
    }

    private void spawnNearOwner(Vec3 anchor) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        position = anchor.add(random.nextDouble(-0.4, 0.4), 0.45, random.nextDouble(-0.4, 0.4));
        previousPosition = position;
        velocity = Vec3.ZERO;
        headingYaw = random.nextFloat() * 360.0F - 180.0F;
        bodyYaw = headingYaw;
        destinationTicks = 0;
        escapeTicks = 0;
    }

    private void pickNewDestination(Vec3 anchor) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vec3 best = anchor;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double radius = random.nextDouble(9.0, 25.0);
            Vec3 point = anchor.add(Math.cos(angle) * radius,
                    random.nextDouble(-1.2, 5.5), Math.sin(angle) * radius);
            Vec3 fromOwner = point.subtract(owner.position());
            if (fromOwner.lengthSqr() >= RETURN_DISTANCE * RETURN_DISTANCE || blocked(point)) {
                continue;
            }
            double visits = exploredCells.getOrDefault(cellKey(point), 0);
            double score = radius * 0.2 - visits * 2.5
                    + Math.min(11.0, point.distanceTo(position)) * 0.5
                    + random.nextDouble(4.0)
                    + (!rayBlocked(position, point) ? 3.0 : 0.0);
            if (score > bestScore) {
                best = point;
                bestScore = score;
            }
        }
        destination = bestScore == Double.NEGATIVE_INFINITY ? anchor.add(0.0, 1.5, 0.0) : best;
        destinationTicks = random.nextInt(95, 260);
        stuckTicks = 0;
        dopamine = Math.min(1.0F, dopamine + 0.15F);
        decisions++;
    }

    private Sensors readSensors() {
        Vec3 ahead = forward(headingYaw, headingPitch);
        Vec3 left = forward(headingYaw - 38.0F, headingPitch);
        Vec3 right = forward(headingYaw + 38.0F, headingPitch);
        boolean front = rayBlocked(position, position.add(ahead.scale(2.45)));
        boolean sideL = rayBlocked(position, position.add(left.scale(1.8)));
        boolean sideR = rayBlocked(position, position.add(right.scale(1.8)));
        boolean above = rayBlocked(position, position.add(0.0, 2.0, 0.0));
        double ground = groundDistance(position);
        float novelty = 1.0F / (1.0F + exploredCells.getOrDefault(cellKey(position), 0) * 0.16F);
        float light = mc.level.getMaxLocalRawBrightness(BlockPos.containing(position)) / 15.0F;
        return new Sensors(front, sideL, sideR, above, ground < 1.1, ground,
                novelty, stamina, light, ownerDistance);
    }

    private int selectAction(Sensors sensors, float desiredYaw, float desiredPitch) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int best = 0;
        float bestScore = -Float.MAX_VALUE;
        float curiosity = 0.12F + sensors.novelty * 0.14F + dopamine * 0.04F;
        for (int a = 0; a < ACTIONS; a++) {
            float[] steering = ACTION_STEERING[a];
            float candidateYaw = headingYaw + steering[0] * 19.0F;
            float candidatePitch = Mth.clamp(headingPitch + steering[1] * 16.0F, -55.0F, 55.0F);
            float align = (float) forward(candidateYaw, candidatePitch)
                    .dot(forward(desiredYaw, desiredPitch));
            float danger = sensors.frontBlocked && steering[0] == 0.0F ? 0.65F : 0.0F;
            if (steering[0] < 0.0F && sensors.leftBlocked) {
                danger += 1.0F;
            }
            if (steering[0] > 0.0F && sensors.rightBlocked) {
                danger += 1.0F;
            }
            if (steering[1] < 0.0F && sensors.upBlocked) {
                danger += 1.1F;
            }
            if (steering[1] > 0.0F && sensors.groundClose) {
                danger += 0.8F;
            }
            float score = learnedActions[sensors.key()][a] * 0.8F
                    + align * (state == State.RETURN ? 1.6F : 0.6F)
                    - danger + (random.nextFloat() - 0.5F) * curiosity;
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        if (state != State.RETURN && !sensors.frontBlocked && random.nextFloat() < curiosity * 0.22F) {
            best = random.nextInt(ACTIONS);
        }
        return best;
    }

    private void updateLearning(Sensors current, Vec3 goal) {
        if (ticks < 3) {
            return;
        }
        double progress = previousGoalDistance - position.distanceTo(goal);
        double travelled = position.distanceTo(previousDecisionPosition);
        float reward = Mth.clamp((float) (progress * 0.8 + travelled * 0.55
                + current.novelty * 0.35 - (current.frontBlocked ? 0.7 : 0.0)
                - (ownerDistance > 25.0 ? 0.8 : 0.0)), -2.0F, 2.0F);
        float old = learnedActions[previousSensorKey][currentAction];
        learnedActions[previousSensorKey][currentAction] = Mth.clamp(old + 0.10F * (reward - old), -2.0F, 2.0F);
        if (reward > 0.6F) {
            dopamine = Math.min(1.0F, dopamine + 0.1F);
        }
    }

    private Vec3 obstacleRepulsion(Vec3 heading) {
        Vec3 sum = Vec3.ZERO;
        Vec3[] directions = {heading, forward(headingYaw - 40.0F, headingPitch),
                forward(headingYaw + 40.0F, headingPitch), new Vec3(0.0, 1.0, 0.0),
                new Vec3(0.0, -1.0, 0.0)};
        for (Vec3 direction : directions) {
            Vec3 end = position.add(direction.scale(1.65));
            HitResult hit = mc.level.clip(new ClipContext(position, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, owner));
            if (hit.getType() != HitResult.Type.MISS) {
                double distance = position.distanceTo(hit.getLocation());
                double strength = Math.max(0.0, (1.65 - distance) / 1.65);
                sum = sum.subtract(direction.scale(strength)).add(0.0, strength * 0.35, 0.0);
            }
        }
        return sum.lengthSqr() > 1.0 ? sum.normalize() : sum;
    }

    private Vec3 moveWithCollision(Vec3 start, Vec3 motion) {
        Vec3 candidate = start.add(motion);
        if (!blocked(candidate) && !rayBlocked(start, candidate)) {
            return candidate;
        }
        obstacleAvoidances++;
        Vec3[] alternatives = {
                start.add(0.0, Math.max(0.3, motion.y + 0.3), 0.0),
                start.add(-motion.z, Math.max(0.18, motion.y), motion.x),
                start.add(motion.z, Math.max(0.18, motion.y), -motion.x),
                start.add(motion.x * 0.4, Math.max(0.3, motion.y), motion.z * 0.4),
                start.add(motion.x * 0.35, Math.min(-0.15, motion.y), motion.z * 0.35)
        };
        for (Vec3 alternative : alternatives) {
            if (!blocked(alternative) && !rayBlocked(start, alternative)) {
                velocity = alternative.subtract(start);
                escapeTicks = Math.max(escapeTicks, 9);
                return alternative;
            }
        }
        velocity = Vec3.ZERO;
        escapeTicks = Math.max(escapeTicks, 15);
        return start;
    }

    private double groundDistance(Vec3 at) {
        Vec3 low = at.add(0.0, -7.0, 0.0);
        HitResult hit = mc.level.clip(new ClipContext(at, low, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS ? 7.0 : at.distanceTo(hit.getLocation());
    }

    private boolean rayBlocked(Vec3 from, Vec3 to) {
        return mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner)).getType() != HitResult.Type.MISS;
    }

    private boolean blocked(Vec3 at) {
        if (!finite(at) || at.y < mc.level.getMinY() + 0.5) {
            return true;
        }
        for (Vec3 probe : BODY_PROBES) {
            BlockPos block = BlockPos.containing(at.add(probe));
            if (!mc.level.getBlockState(block).getCollisionShape(mc.level, block).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private int recordVisit(Vec3 at) {
        long key = cellKey(at);
        int count = exploredCells.merge(key, 1, (oldValue, increment) -> Math.min(250, oldValue + increment));
        if (exploredCells.size() > 4096) {
            exploredCells.clear();
        }
        return count;
    }

    private static long cellKey(Vec3 at) {
        int x = Mth.floor(at.x / 4.0);
        int y = Mth.floor(at.y / 4.0);
        int z = Mth.floor(at.z / 4.0);
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFFL);
    }

    private static Vec3 forward(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRad);
        return new Vec3(-Math.sin(yawRad) * horizontal, -Math.sin(pitchRad),
                Math.cos(yawRad) * horizontal);
    }

    private void changeState(State next) {
        if (state == next) {
            return;
        }
        if (next == State.RETURN) {
            returns++;
        }
        state = next;
        stateTicks = 0;
        decisions++;
    }

    private boolean validTarget() {
        return target != null && !target.isRemoved() && target.isAlive() && target.level() == mc.level;
    }

    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    public Vec3 getPos(float partialTick) {
        if (position == null) {
            return owner == null ? Vec3.ZERO : owner.position().add(0.0, 1.65, 0.0);
        }
        Vec3 previous = previousPosition == null ? position : previousPosition;
        Vec3 shown = previous.lerp(position, Mth.clamp(partialTick, 0.0F, 1.0F));
        if (owner != null && shown.distanceToSqr(owner.position()) > MAX_DISTANCE * MAX_DISTANCE) {
            return owner.position().add(shown.subtract(owner.position()).normalize().scale(MAX_DISTANCE));
        }
        return shown;
    }

    public Vec3 getPos() { return getPos(1.0F); }
    public float getBody() { return bodyYaw; }
    public float getYaw() { return headYaw; }
    public float getPitch() { return headPitch; }
    public float wingFrequency() { return wingFrequency; }
    public float wingPhase(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), previousWingPhase, wingPhase);
    }
    public float legPhase(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), previousLegPhase, legPhase);
    }
    public float legAmount() { return legAmount; }
    public float bodyPitch() { return bodyPitch; }
    public float bodyRoll() { return bodyRoll; }
    public State state() { return state; }
    public Statistics statistics() {
        return new Statistics(state, ownerDistance, velocity.length() * 20.0, wingFrequency,
                stamina, decisions, obstacleAvoidances, returns, flightTicks, targetDistance,
                state == State.CHASE, obstacleAhead);
    }
}
