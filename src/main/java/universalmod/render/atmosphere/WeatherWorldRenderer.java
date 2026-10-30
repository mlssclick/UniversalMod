package universalmod.render.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import universalmod.module.render.Atmosphere;
import universalmod.render.ClientPipelines;
import universalmod.render.WorldVertex;
import universalmod.render.color.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

 
public final class WeatherWorldRenderer {
    private static final RenderType BOLTS = ClientPipelines.WORLD_PARTICLES_GLOW.apply(tex("weather_lightning.png"));
    private static final RenderType BOLT_CORES = ClientPipelines.WORLD_HEALTH_ICONS.apply(tex("weather_lightning.png"));
    private static final List<Strike> LIGHTNING = new ArrayList<>(3);
    private static final SplittableRandom RANDOM = new SplittableRandom(0x6E291C42L);
    private static final SoundEvent[] THUNDER = {
        thunder("weather.thunder_near"), thunder("weather.thunder_mid"), thunder("weather.thunder_far")
    };
    private static final SoundEvent[] RAIN_MATERIAL = {
        sound("weather.rain_stone"), sound("weather.rain_wood"),
        sound("weather.rain_metal"), sound("weather.rain_soft"), sound("weather.rain_water")
    };
    private static final SoundEvent RAIN_AMBIENT = sound("weather.rain_ambient");
    private static ClientLevel world;
    private static boolean thunderMode;
    private static long lastCycle = Long.MIN_VALUE;
    private static long lastRainTick = Long.MIN_VALUE;
    private static Vec3 cycleAnchor = Vec3.ZERO;

    private static final class BoltLine {
        final Vec3 a,b;
        final float width;
        BoltLine(Vec3 a,Vec3 b,float width) { this.a=a; this.b=b;this.width=width; }
    }
    private static final class Strike {
        double x,y,z;
        long startTick, thunderTick;
        float strength,pitch;
        boolean played;
        int thunderKind;
        final List<BoltLine> lines = new ArrayList<>(36);
    }

    private WeatherWorldRenderer() { }
    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("universalmod","textures/atmosphere/"+name);
    }
    private static SoundEvent sound(String path) {
        return SoundEvent.createFixedRangeEvent(Identifier.fromNamespaceAndPath("universalmod",path),32.0f);
    }
    private static SoundEvent thunder(String path) {
         
         
        return SoundEvent.createFixedRangeEvent(Identifier.fromNamespaceAndPath("universalmod",path),128.0f);
    }

    public static void reset() {
        AtmosphereRainRenderer.reset();
        LIGHTNING.clear(); world=null;
        lastCycle=Long.MIN_VALUE;lastRainTick=Long.MIN_VALUE;
    }
    private static double surface(ClientLevel w,double x,double z) {
        return w.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,Mth.floor(x),Mth.floor(z));
    }
     
     
    public static void tick(Atmosphere atmosphere,ClientLevel level,Vec3 camera) {
        if (level==null || atmosphere==null || !atmosphere.isRainActive()) {
            if (world!=null) reset();
            return;
        }
        boolean thunder=atmosphere.isThunderActive();
        if (world!=level || thunderMode!=thunder) {
            reset();world=level;thunderMode=thunder;
        }
        long tick=level.getGameTime();
        if (thunder) {
            long cycle=tick/300L;
            if (cycle!=lastCycle) {
                 
                 
                 
                long cycleStart = lastCycle==Long.MIN_VALUE ? tick+3L : cycle*300L+1L;
                lastCycle=cycle;
                cycleAnchor=camera;
                LIGHTNING.clear();
                SplittableRandom roll=new SplittableRandom(cycle*0x9E3779B97F4A7C15L+0x43B112A7L);
                int count=1+roll.nextInt(3);
                for(int i=0;i<count;i++) {
                    Strike strike=generateStrike(level,camera,cycleStart+i*3L+roll.nextInt(4),roll);
                    LIGHTNING.add(strike);
                }
            }
            for (Strike strike:LIGHTNING) {
                if (!strike.played && tick>=strike.thunderTick) {
                    strike.played=true;
                    if (atmosphere.getThunderVolume()>0f) {
                        level.playLocalSound(strike.x,strike.y,strike.z,THUNDER[strike.thunderKind],
                                SoundSource.WEATHER,atmosphere.getThunderVolume()*(0.58f+strike.strength*0.32f),
                                strike.pitch,false);
                    }
                }
            }
        }
         
         
        if (lastRainTick==Long.MIN_VALUE || tick-lastRainTick>=26) {
            lastRainTick=tick;
            double x=camera.x+(RANDOM.nextDouble()-0.5)*22;
            double z=camera.z+(RANDOM.nextDouble()-0.5)*22;
            double y=surface(level,x,z)+0.2;
            if (Math.abs(y-camera.y)<16) {
                int material=material(level,new BlockPos(Mth.floor(x),Mth.floor(y)-1,Mth.floor(z)));
                float rainAmount=Math.max(0.05f,atmosphere.getRainAmount());
                level.playLocalSound(x,y,z,RAIN_MATERIAL[material],SoundSource.WEATHER,
                        (0.27f+RANDOM.nextFloat()*0.15f)*rainAmount,
                        0.88f+RANDOM.nextFloat()*0.24f,false);
                if (tick%104<26) {
                    double bx=camera.x+(RANDOM.nextDouble()-0.5)*15;
                    double bz=camera.z+(RANDOM.nextDouble()-0.5)*15;
                    level.playLocalSound(bx,camera.y+3,bz,RAIN_AMBIENT,SoundSource.WEATHER,
                            rainAmount*0.38f,0.96f+RANDOM.nextFloat()*0.07f,false);
                }
            }
        }
    }

    private static int material(ClientLevel level,BlockPos pos) {
        BlockState state=level.getBlockState(pos);
        if (!level.getFluidState(pos).isEmpty()) return 4;
        SoundType type=state.getSoundType();
        if (type==SoundType.METAL || type==SoundType.COPPER || type==SoundType.ANVIL) return 2;
        if (type==SoundType.WOOD || type==SoundType.BAMBOO_WOOD || type==SoundType.CHERRY_WOOD) return 1;
        if (type==SoundType.GRASS || type==SoundType.SAND || type==SoundType.WOOL || type==SoundType.SNOW) return 3;
        return 0;
    }

    private static Strike generateStrike(ClientLevel level,Vec3 cam,long start,SplittableRandom rand) {
        Strike strike=new Strike();
        double azimuth=rand.nextDouble()*Math.PI*2.0;
        double radius=27.0+rand.nextDouble()*47.0;
        strike.x=cam.x+Math.cos(azimuth)*radius;
        strike.z=cam.z+Math.sin(azimuth)*radius;
        double bottom=surface(level,strike.x,strike.z)+rand.nextDouble()*8.0;
        strike.y=bottom+rand.nextDouble()*13.0;
        strike.startTick=start;
        strike.strength=0.65f+rand.nextFloat()*0.35f;
        strike.pitch=0.78f+rand.nextFloat()*0.30f;
        strike.thunderKind=radius<41?0:radius<58?1:2;
         
         
        strike.thunderTick=start+Math.max(3L,Math.round(radius*0.42));
        Vec3 prev=new Vec3(strike.x,bottom+57.0+rand.nextDouble()*33.0,strike.z);
        Vec3 end=new Vec3(strike.x,bottom,strike.z);
        int segments=21+rand.nextInt(10);
        double ox=0,oz=0;
        List<Vec3> main=new ArrayList<>(segments+1);
        main.add(prev);
        for(int i=1;i<=segments;i++) {
            double f=i/(double)segments;
            ox=ox*0.55+(rand.nextDouble()-0.5)*5.4;
            oz=oz*0.55+(rand.nextDouble()-0.5)*4.9;
            Vec3 next=new Vec3(end.x+ox*(1-f*0.2),Mth.lerp(f,prev.y,end.y),end.z+oz*(1-f*0.2));
             
            next=new Vec3(next.x,Mth.lerp(f,main.get(0).y,end.y),next.z);
            main.add(next);
             
             
            float trunkWidth=(0.095f+0.075f*(float)f)*(0.85f+rand.nextFloat()*0.30f);
            strike.lines.add(new BoltLine(main.get(i-1),next,trunkWidth));
        }
        int primaryForks=5+rand.nextInt(5);
        for(int branch=0;branch<primaryForks;branch++) {
            int at=2+rand.nextInt(segments-6);
            Vec3 bPrev=main.get(at);
            double angle=rand.nextDouble()*Math.PI*2;
            double vx=Math.cos(angle),vz=Math.sin(angle);
            int n=3+rand.nextInt(6);
            for(int k=0;k<n;k++) {
                float taper=1f-(float)k/(n+0.5f);
                Vec3 bNext=bPrev.add(vx*(1.4+rand.nextDouble()*2.1),
                        -0.8-rand.nextDouble()*3.0,vz*(1.1+rand.nextDouble()*2.0));
                float width=(0.040f+rand.nextFloat()*0.068f)*taper;
                strike.lines.add(new BoltLine(bPrev,bNext,width));
                 
                if(k>=1 && k<n-1 && rand.nextDouble()<0.32) {
                    Vec3 twig=bNext.add((rand.nextDouble()-.5)*4.8,
                            -1.1-rand.nextDouble()*2.4,(rand.nextDouble()-.5)*4.8);
                    strike.lines.add(new BoltLine(bNext,twig,width*0.44f));
                }
                bPrev=bNext;
                angle+=(rand.nextDouble()-0.5)*0.85;
                vx=Math.cos(angle);vz=Math.sin(angle);
            }
        }
        return strike;
    }

    public static void render(Atmosphere atmosphere,Camera camera,Matrix4f view,float partialTick) {
        Minecraft mc=Minecraft.getInstance();
        if (mc.level==null || !atmosphere.isRainActive()) return;
        Vec3 cam=camera.position();
        if (world!=mc.level) {reset();world=mc.level;thunderMode=atmosphere.isThunderActive();}
        PoseStack pose=new PoseStack();pose.mulPose(new Matrix4f(view));
        MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        if (atmosphere.isThunderActive()) renderLightning(mc.level,cam,pose,buffers,partialTick);
    }

    public static float flash(float partialTick) {
        if (world == null || !thunderMode) return 0f;
        float now = world.getGameTime() + partialTick;
        float flash = 0f;
        for (Strike strike : LIGHTNING) {
            float age = now - strike.startTick;
            if (age < 0f || age > 11f) continue;
            float pulse = (float)(Math.exp(-Math.pow((age - 1.0) * 1.20, 2.0))
                    + 0.54 * Math.exp(-Math.pow((age - 5.3) * 1.14, 2.0))
                    + 0.25 * Math.exp(-Math.pow((age - 8.0) * 1.6, 2.0)));
            flash = Math.max(flash, pulse * strike.strength);
        }
        return Mth.clamp(flash, 0f, 1f);
    }

    private static void renderLightning(ClientLevel world,Vec3 cam,PoseStack pose,
                                        MultiBufferSource.BufferSource buffers,float partialTick) {
        if (LIGHTNING.isEmpty()) return;
        float tick=world.getGameTime()+partialTick;
        VertexConsumer glow=buffers.getBuffer(BOLTS);
        for (Strike strike:LIGHTNING) {
            float age=tick-strike.startTick;
            if(age<0 || age>11f)continue;
            float pulse=(float)(Math.exp(-Math.pow((age-1.0)*1.20,2.0))
                    +0.54*Math.exp(-Math.pow((age-5.3)*1.14,2.0))
                    +0.25*Math.exp(-Math.pow((age-8.0)*1.6,2.0)));
            if(pulse<0.025f)continue;
            int alpha=Mth.clamp(Math.round(175*pulse*strike.strength),0,238);
            int color=ColorUtil.rgba(154,185,255,alpha);
            for(BoltLine segment:strike.lines) boltQuad(glow,pose.last(),cam,segment,segment.width*4.4f,color);
        }
        buffers.endBatch(BOLTS);
        VertexConsumer core=buffers.getBuffer(BOLT_CORES);
        for(Strike strike:LIGHTNING) {
            float age=tick-strike.startTick;
            if(age<0 || age>11f)continue;
            float pulse=(float)(Math.exp(-Math.pow((age-1.0)*1.20,2.0))
                    +0.54*Math.exp(-Math.pow((age-5.3)*1.14,2.0))
                    +0.25*Math.exp(-Math.pow((age-8.0)*1.6,2.0)));
            if(pulse<0.025f)continue;
            int color=ColorUtil.rgba(230,244,255,Mth.clamp(Math.round(255*pulse),0,255));
            for(BoltLine segment:strike.lines) boltQuad(core,pose.last(),cam,segment,segment.width,color);
        }
        buffers.endBatch(BOLT_CORES);
    }
    private static void boltQuad(VertexConsumer out,PoseStack.Pose matrix,Vec3 camera,BoltLine line,float width,int color) {
        Vec3 a=line.a.subtract(camera),b=line.b.subtract(camera);
        Vec3 axis=b.subtract(a);
        Vec3 toward=a.add(b).scale(-0.5);  
        Vec3 right=axis.cross(toward);
        double len=right.length();
        if(len<0.001)return;
        right=right.scale(width/len);
        WorldVertex.textured(out,matrix,(float)(a.x-right.x),(float)(a.y-right.y),(float)(a.z-right.z),0,1,color);
        WorldVertex.textured(out,matrix,(float)(b.x-right.x),(float)(b.y-right.y),(float)(b.z-right.z),0,0,color);
        WorldVertex.textured(out,matrix,(float)(b.x+right.x),(float)(b.y+right.y),(float)(b.z+right.z),1,0,color);
        WorldVertex.textured(out,matrix,(float)(a.x+right.x),(float)(a.y+right.y),(float)(a.z+right.z),1,1,color);
    }
}
