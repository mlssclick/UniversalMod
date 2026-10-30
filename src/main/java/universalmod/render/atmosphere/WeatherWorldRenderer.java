package universalmod.render.atmosphere;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.block.BlockState;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import universalmod.module.render.Atmosphere;
import universalmod.render.WorldVertex;
import universalmod.render.color.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public final class WeatherWorldRenderer {
    private static final RenderLayer BOLTS = RenderLayer.getEyes(tex("weather_lightning.png"));
    private static final RenderLayer BOLT_CORES = RenderLayer.getEntityTranslucent(tex("weather_lightning.png"));
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
    private static ClientWorld world;
    private static boolean thunderMode;
    private static long lastCycle = Long.MIN_VALUE;
    private static long lastRainTick = Long.MIN_VALUE;
    private static Vec3d cycleAnchor = Vec3d.ZERO;

    private static final class BoltLine {
        final Vec3d a,b;
        final float width;
        BoltLine(Vec3d a,Vec3d b,float width) { this.a=a; this.b=b;this.width=width; }
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
        return Identifier.of("universalmod","textures/atmosphere/"+name);
    }
    private static SoundEvent sound(String path) {
        return SoundEvent.of(Identifier.of("universalmod",path),32.0f);
    }
    private static SoundEvent thunder(String path) {

        return SoundEvent.of(Identifier.of("universalmod",path),128.0f);
    }

    public static void reset() {
        AtmosphereRainRenderer.reset();
        LIGHTNING.clear(); world=null;
        lastCycle=Long.MIN_VALUE;lastRainTick=Long.MIN_VALUE;
    }
    private static double surface(ClientWorld w,double x,double z) {
        return w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,MathHelper.floor(x),MathHelper.floor(z));
    }

    public static void tick(Atmosphere atmosphere,ClientWorld level,Vec3d camera) {
        if (level==null || atmosphere==null || !atmosphere.isRainActive()) {
            if (world!=null) reset();
            return;
        }
        boolean thunder=atmosphere.isThunderActive();
        if (world!=level || thunderMode!=thunder) {
            reset();world=level;thunderMode=thunder;
        }
        long tick=level.getTime();
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
                        level.playSound(strike.x,strike.y,strike.z,THUNDER[strike.thunderKind],
                                SoundCategory.WEATHER,atmosphere.getThunderVolume()*(0.58f+strike.strength*0.32f),
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
                int material=material(level,new BlockPos(MathHelper.floor(x),MathHelper.floor(y)-1,MathHelper.floor(z)));
                float rainAmount=Math.max(0.05f,atmosphere.getRainAmount());
                level.playSound(x,y,z,RAIN_MATERIAL[material],SoundCategory.WEATHER,
                        (0.27f+RANDOM.nextFloat()*0.15f)*rainAmount,
                        0.88f+RANDOM.nextFloat()*0.24f,false);
                if (tick%104<26) {
                    double bx=camera.x+(RANDOM.nextDouble()-0.5)*15;
                    double bz=camera.z+(RANDOM.nextDouble()-0.5)*15;
                    level.playSound(bx,camera.y+3,bz,RAIN_AMBIENT,SoundCategory.WEATHER,
                            rainAmount*0.38f,0.96f+RANDOM.nextFloat()*0.07f,false);
                }
            }
        }
    }

    private static int material(ClientWorld level,BlockPos pos) {
        BlockState state=level.getBlockState(pos);
        if (!level.getFluidState(pos).isEmpty()) return 4;
        BlockSoundGroup type=state.getSoundGroup();
        if (type==BlockSoundGroup.METAL || type==BlockSoundGroup.COPPER || type==BlockSoundGroup.ANVIL) return 2;
        if (type==BlockSoundGroup.WOOD || type==BlockSoundGroup.BAMBOO_WOOD || type==BlockSoundGroup.CHERRY_WOOD) return 1;
        if (type==BlockSoundGroup.GRASS || type==BlockSoundGroup.SAND || type==BlockSoundGroup.WOOL || type==BlockSoundGroup.SNOW) return 3;
        return 0;
    }

    private static Strike generateStrike(ClientWorld level,Vec3d cam,long start,SplittableRandom rand) {
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
        Vec3d prev=new Vec3d(strike.x,bottom+57.0+rand.nextDouble()*33.0,strike.z);
        Vec3d end=new Vec3d(strike.x,bottom,strike.z);
        int segments=21+rand.nextInt(10);
        double ox=0,oz=0;
        List<Vec3d> main=new ArrayList<>(segments+1);
        main.add(prev);
        for(int i=1;i<=segments;i++) {
            double f=i/(double)segments;
            ox=ox*0.55+(rand.nextDouble()-0.5)*5.4;
            oz=oz*0.55+(rand.nextDouble()-0.5)*4.9;
            Vec3d next=new Vec3d(end.x+ox*(1-f*0.2),MathHelper.lerp(f,prev.y,end.y),end.z+oz*(1-f*0.2));

            next=new Vec3d(next.x,MathHelper.lerp(f,main.get(0).y,end.y),next.z);
            main.add(next);

            float trunkWidth=(0.095f+0.075f*(float)f)*(0.85f+rand.nextFloat()*0.30f);
            strike.lines.add(new BoltLine(main.get(i-1),next,trunkWidth));
        }
        int primaryForks=5+rand.nextInt(5);
        for(int branch=0;branch<primaryForks;branch++) {
            int at=2+rand.nextInt(segments-6);
            Vec3d bPrev=main.get(at);
            double angle=rand.nextDouble()*Math.PI*2;
            double vx=Math.cos(angle),vz=Math.sin(angle);
            int n=3+rand.nextInt(6);
            for(int k=0;k<n;k++) {
                float taper=1f-(float)k/(n+0.5f);
                Vec3d bNext=bPrev.add(vx*(1.4+rand.nextDouble()*2.1),
                        -0.8-rand.nextDouble()*3.0,vz*(1.1+rand.nextDouble()*2.0));
                float width=(0.040f+rand.nextFloat()*0.068f)*taper;
                strike.lines.add(new BoltLine(bPrev,bNext,width));

                if(k>=1 && k<n-1 && rand.nextDouble()<0.32) {
                    Vec3d twig=bNext.add((rand.nextDouble()-.5)*4.8,
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
        MinecraftClient mc=MinecraftClient.getInstance();
        if (mc.world==null || !atmosphere.isRainActive()) return;
        Vec3d cam=camera.getPos();
        if (world!=mc.world) {reset();world=mc.world;thunderMode=atmosphere.isThunderActive();}
        MatrixStack pose=new MatrixStack();pose.multiplyPositionMatrix(new Matrix4f(view));
        VertexConsumerProvider.Immediate buffers=mc.getBufferBuilders().getEntityVertexConsumers();
        if (atmosphere.isThunderActive()) renderLightning(mc.world,cam,pose,buffers,partialTick);
    }

    public static float flash(float partialTick) {
        if (world == null || !thunderMode) return 0f;
        float now = world.getTime() + partialTick;
        float flash = 0f;
        for (Strike strike : LIGHTNING) {
            float age = now - strike.startTick;
            if (age < 0f || age > 11f) continue;
            float pulse = (float)(Math.exp(-Math.pow((age - 1.0) * 1.20, 2.0))
                    + 0.54 * Math.exp(-Math.pow((age - 5.3) * 1.14, 2.0))
                    + 0.25 * Math.exp(-Math.pow((age - 8.0) * 1.6, 2.0)));
            flash = Math.max(flash, pulse * strike.strength);
        }
        return MathHelper.clamp(flash, 0f, 1f);
    }

    private static void renderLightning(ClientWorld world,Vec3d cam,MatrixStack pose,
                                        VertexConsumerProvider.Immediate buffers,float partialTick) {
        if (LIGHTNING.isEmpty()) return;
        float tick=world.getTime()+partialTick;
        VertexConsumer glow=buffers.getBuffer(BOLTS);
        for (Strike strike:LIGHTNING) {
            float age=tick-strike.startTick;
            if(age<0 || age>11f)continue;
            float pulse=(float)(Math.exp(-Math.pow((age-1.0)*1.20,2.0))
                    +0.54*Math.exp(-Math.pow((age-5.3)*1.14,2.0))
                    +0.25*Math.exp(-Math.pow((age-8.0)*1.6,2.0)));
            if(pulse<0.025f)continue;
            int alpha=MathHelper.clamp(Math.round(175*pulse*strike.strength),0,238);
            int color=ColorUtil.rgba(154,185,255,alpha);
            for(BoltLine segment:strike.lines) boltQuad(glow,pose.peek(),cam,segment,segment.width*4.4f,color);
        }
        buffers.draw(BOLTS);
        VertexConsumer core=buffers.getBuffer(BOLT_CORES);
        for(Strike strike:LIGHTNING) {
            float age=tick-strike.startTick;
            if(age<0 || age>11f)continue;
            float pulse=(float)(Math.exp(-Math.pow((age-1.0)*1.20,2.0))
                    +0.54*Math.exp(-Math.pow((age-5.3)*1.14,2.0))
                    +0.25*Math.exp(-Math.pow((age-8.0)*1.6,2.0)));
            if(pulse<0.025f)continue;
            int color=ColorUtil.rgba(230,244,255,MathHelper.clamp(Math.round(255*pulse),0,255));
            for(BoltLine segment:strike.lines) boltQuad(core,pose.peek(),cam,segment,segment.width,color);
        }
        buffers.draw(BOLT_CORES);
    }
    private static void boltQuad(VertexConsumer out,MatrixStack.Entry matrix,Vec3d camera,BoltLine line,float width,int color) {
        Vec3d a=line.a.subtract(camera),b=line.b.subtract(camera);
        Vec3d axis=b.subtract(a);
        Vec3d toward=a.add(b).multiply(-0.5);
        Vec3d right=axis.crossProduct(toward);
        double len=right.length();
        if(len<0.001)return;
        right=right.multiply(width/len);
        WorldVertex.textured(out,matrix,(float)(a.x-right.x),(float)(a.y-right.y),(float)(a.z-right.z),0,1,color);
        WorldVertex.textured(out,matrix,(float)(b.x-right.x),(float)(b.y-right.y),(float)(b.z-right.z),0,0,color);
        WorldVertex.textured(out,matrix,(float)(b.x+right.x),(float)(b.y+right.y),(float)(b.z+right.z),1,0,color);
        WorldVertex.textured(out,matrix,(float)(a.x+right.x),(float)(a.y+right.y),(float)(a.z+right.z),1,1,color);
    }
}
