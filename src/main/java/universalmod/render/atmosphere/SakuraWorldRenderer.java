package universalmod.render.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import universalmod.module.render.Atmosphere;
import universalmod.render.ClientPipelines;
import universalmod.render.Render3D;
import universalmod.render.WorldVertex;
import universalmod.render.color.ColorUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

 
public final class SakuraWorldRenderer {
    private static final Identifier SAKURA = Identifier.fromNamespaceAndPath("universalmod", "textures/atmosphere/sakura_petal.png");
    private static final Identifier AUTUMN = Identifier.fromNamespaceAndPath("universalmod", "textures/atmosphere/autumn_leaf.png");
    private static final RenderType SAKURA_RENDER = ClientPipelines.WORLD_HEALTH_ICONS.apply(SAKURA);
    private static final RenderType AUTUMN_RENDER = ClientPipelines.WORLD_HEALTH_ICONS.apply(AUTUMN);
    private static final float RADIUS = 32f;
    private static final int MAX_LEAVES = 3600;
    private static final List<Petal> PETALS = new ArrayList<>(MAX_LEAVES);
    private static final SplittableRandom RANDOM = new SplittableRandom(0x5A44A222L);
    private static ClientLevel level;
    private static boolean autumnMode;
    private static long lastNanos, lastSupportCheck;
    private static float emissionClock;
    private static int generation, supportPhase;

    private static final class Petal {
        double x, y, z, targetY, landingFromY;
        float vy, size, roll, spin, sway, phase, drift, tone, opacity, age, settling, retireAt;
        boolean settled, landing, retiring;
        int step;
    }
    private SakuraWorldRenderer() { }

    public static void reset() {
        PETALS.clear(); level = null; lastNanos = 0; lastSupportCheck = 0;
        emissionClock = 0; generation = 0; supportPhase = 0;
    }

    private static double ground(ClientLevel world, double x, double z) {
        return Math.max(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(x), Mth.floor(z)), world.getMinY() + 1) + 0.028;
    }

    private static Petal spawn(ClientLevel world, Vec3 cam, boolean initial) {
        Petal p = new Petal();
        double angle = RANDOM.nextDouble() * Math.PI * 2.0;
        double radius = Math.sqrt(RANDOM.nextDouble()) * (RADIUS - 2.0);
        p.x = cam.x + Math.cos(angle) * radius;
        p.z = cam.z + Math.sin(angle) * radius;
        p.targetY = ground(world, p.x, p.z);
        double ceiling = Math.max(cam.y + 17.0, p.targetY + 17.0);
        p.y = initial ? p.targetY + RANDOM.nextDouble() * Math.min(ceiling-p.targetY, 20.0)
                      : ceiling + RANDOM.nextDouble() * 9.0;
        p.settled = initial && RANDOM.nextDouble() < 0.56;
        if (p.settled) p.y = p.targetY;
        p.vy = autumnMode ? (-0.65f-RANDOM.nextFloat()*0.55f)
                          : (-0.32f - RANDOM.nextFloat()*0.44f);
        p.size = (autumnMode ? 0.150f : 0.062f) + RANDOM.nextFloat() * (autumnMode ? 0.104f : 0.046f);
        p.roll = RANDOM.nextFloat() * 360.0f;
        p.spin = (RANDOM.nextBoolean() ? -1f : 1f) *
                (autumnMode ? 64f + RANDOM.nextFloat()*105f : 9f + RANDOM.nextFloat()*38f);
        p.sway = 0.35f + RANDOM.nextFloat() * 0.85f;
        p.phase = RANDOM.nextFloat() * Mth.TWO_PI;
        p.drift = RANDOM.nextFloat() * 0.72f + 0.2f;
        p.tone = RANDOM.nextFloat();
        p.opacity = initial && p.settled ? 1f : 0f;
        p.step = generation++ & 3;
        return p;
    }

    public static void render(Atmosphere atmosphere, Camera camera, Matrix4f view, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null ||
                !(atmosphere.isSakuraActive() || atmosphere.isAutumnActive())) return;
        ClientLevel world = mc.level;
        Vec3 cam = camera.position();
        boolean autumn = atmosphere.isAutumnActive();
        if (world != level || autumnMode != autumn) {
            reset(); level = world; autumnMode = autumn;
        }
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Mth.clamp((now-lastNanos)*1.0e-9f, 0f, 0.065f);
        lastNanos = now;
        float t = (world.getGameTime() + partialTick) * 0.05f;
         
         
         
        int fallingTarget = Math.max(0, atmosphere.getSakuraPetals());
        int groundCapacity = Math.min(MAX_LEAVES - fallingTarget, fallingTarget * 4);
        if (PETALS.isEmpty() && fallingTarget > 0) {
             
            int initialSettled = Math.min(groundCapacity, Math.round(groundCapacity * 0.66f));
            for (int i=0; i<initialSettled; i++) {
                Petal p = spawn(world, cam, true);
                p.settled = true; p.landing = false; p.y = p.targetY;
                p.opacity = 1f; p.age = 0.4f + RANDOM.nextFloat()*8.0f;
                PETALS.add(p);
            }
            for (int i=0; i<fallingTarget; i++) PETALS.add(spawn(world, cam, false));
        }
        float wind = atmosphere.getSakuraWind(), speed = atmosphere.getSakuraFallSpeed();
        boolean checkSupport = lastSupportCheck == 0 || now-lastSupportCheck > 60_000_000L;
        if (checkSupport) {
            lastSupportCheck = now;
            supportPhase = (supportPhase+1)&3;
        }
         
         
         
        int falling = 0, grounded = 0;
        Petal oldest = null;
        for (Petal p : PETALS) {
            if (!p.settled && !p.landing && !p.retiring) falling++;
            if (p.settled) {
                grounded++;
                if (!p.retiring && (oldest == null || p.age > oldest.age)) oldest = p;
            }
        }
        emissionClock += dt;
        while (emissionClock >= 0.11f && falling < fallingTarget) {
            emissionClock -= 0.11f;
            if (PETALS.size() < Math.min(MAX_LEAVES, groundCapacity + fallingTarget + 64)) {
                PETALS.add(spawn(world, cam, false));
                falling++;
            } else if (oldest != null) {
                 
                 
                int index = PETALS.indexOf(oldest);
                if (index >= 0) {
                    PETALS.set(index, spawn(world, cam, false)); falling++; grounded--;
                    oldest = null;
                }
            } else break;
        }
        if (grounded > groundCapacity && oldest != null) {
            oldest.retiring = true;
            oldest.retireAt = oldest.age;
        }
         
        if (PETALS.size() > Math.min(MAX_LEAVES, groundCapacity + fallingTarget + 64)
                && oldest != null && !oldest.retiring) {
            oldest.retiring = true; oldest.retireAt = oldest.age;
        }
        PoseStack pose = new PoseStack();
        pose.mulPose(new Matrix4f(view));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType renderType = autumn ? AUTUMN_RENDER : SAKURA_RENDER;
        VertexConsumer vertices = buffers.getBuffer(renderType);
        Quaternionf cameraFacing = new Quaternionf(Render3D.lastCameraRotation);
        for (int i=0; i<PETALS.size(); i++) {
            Petal p = PETALS.get(i);
            p.age += dt;
            double dx = p.x-cam.x, dz = p.z-cam.z;
            if (dx*dx+dz*dz > (RADIUS+4)*(RADIUS+4) || p.y < world.getMinY()+1 || p.y > cam.y+49) {
                PETALS.set(i,spawn(world,cam,false));
                continue;
            }
            if (checkSupport && p.step == supportPhase) {
                double top = ground(world,p.x,p.z);
                if (top < p.targetY - 0.08 && (p.settled || p.landing)) {
                    p.settled = false; p.landing = false; p.settling = 0f; p.vy = -0.22f;
                }
                p.targetY = top;
                if (p.settled && top > p.y + 0.08) {
                    p.settled = false; p.landing = false; p.vy = -0.17f;
                }
            }
            if (!p.settled && !p.landing) {
                float gust = (float)Math.pow(0.5+0.5*Math.sin(t*0.53f+p.phase*2.2f), 5.0);
                float lateral = wind*(0.30f+1.18f*gust);
                p.x += (Math.sin(t*0.69f+p.phase)*p.sway*0.18f+lateral*(0.22f+p.drift*0.16f))*dt;
                p.z += (Math.cos(t*0.57f+p.phase*1.2f)*p.sway*0.16f+lateral*0.23f)*dt;
                p.vy = Math.max(-(autumn ? 2.40f : 1.43f)*speed,
                        p.vy-(autumn ? 0.92f : 0.30f)*speed*dt);
                p.y += (p.vy+gust*wind*0.095f)*dt;
                p.roll += p.spin*dt;
                 
                if (p.y <= p.targetY+0.27f) {
                    p.landing = true; p.settling = 0f;
                    p.landingFromY = Math.max(p.y,p.targetY+0.08f);
                    p.vy = 0f;
                }
            }
            if (p.landing) {
                p.settling = Math.min(1f,p.settling+dt*2.8f);
                float ease = p.settling*p.settling*(3f-2f*p.settling);
                p.y = p.landingFromY+(p.targetY-p.landingFromY)*ease;
                p.roll += p.spin*dt*(1f-ease);
                if (p.settling>=1f) {
                    p.y=p.targetY; p.landing=false; p.settled=true;
                }
            }
            float lifeAlpha = p.retiring ? Math.max(0f,1f-(p.age-p.retireAt)*0.29f) : 1f;
            if (p.retiring && lifeAlpha <= 0.01f) {
                PETALS.remove(i--); continue;
            }
            p.opacity = Math.min(1f,p.opacity+dt*1.55f);
            float horizontal = (float)Math.sqrt(dx*dx+dz*dz);
            float distanceFade = 1f-Mth.clamp((horizontal-24.0f)/7.5f,0f,1f);
            int alpha=Math.round(220f*p.opacity*distanceFade*lifeAlpha);
            if (alpha<=3) continue;
            int color=tint(p.tone,alpha,autumn);
            pose.pushPose();
            pose.translate(p.x-cam.x,p.y-cam.y,p.z-cam.z);
            if (p.settled) {
                pose.mulPose(Axis.YP.rotationDegrees(p.roll));
                pose.mulPose(Axis.XP.rotationDegrees(-90f));
            } else if (p.landing) {
                pose.mulPose(cameraFacing);
                pose.mulPose(Axis.ZP.rotationDegrees(p.roll));
                pose.mulPose(Axis.XP.rotationDegrees(-90f*p.settling));
            } else {
                pose.mulPose(cameraFacing);
                pose.mulPose(Axis.ZP.rotationDegrees(p.roll));
                pose.mulPose(Axis.XP.rotationDegrees(
                        (autumn ? 48f : 22f)*(float)Math.sin(t*(autumn?2.65:1.5)+p.phase)));
                if (autumn) pose.mulPose(Axis.YP.rotationDegrees(
                        38f*(float)Math.cos(t*2.17+p.phase*1.72f)));
            }
            sprite(vertices,pose.last(),p.size,color);
            pose.popPose();
        }
        buffers.endBatch(renderType);
    }

    private static int tint(float value,int alpha,boolean autumn) {
        if (autumn) {
            if (value<.24f) return ColorUtil.rgba(255,191,48,alpha);
            if (value<.51f) return ColorUtil.rgba(236,120,30,alpha);
            if (value<.77f) return ColorUtil.rgba(194,75,31,alpha);
            return ColorUtil.rgba(248,220,100,alpha);
        }
        if (value<.27f) return ColorUtil.rgba(255,222,231,alpha);
        if (value<.52f) return ColorUtil.rgba(255,186,207,alpha);
        if (value<.79f) return ColorUtil.rgba(251,161,195,alpha);
        return ColorUtil.rgba(255,236,237,alpha);
    }
    private static void sprite(VertexConsumer out,PoseStack.Pose pose,float size,int tint) {
        WorldVertex.textured(out,pose,-size,-size,0,0,1,tint);
        WorldVertex.textured(out,pose, size,-size,0,1,1,tint);
        WorldVertex.textured(out,pose, size, size,0,1,0,tint);
        WorldVertex.textured(out,pose,-size, size,0,0,0,tint);
    }
}
