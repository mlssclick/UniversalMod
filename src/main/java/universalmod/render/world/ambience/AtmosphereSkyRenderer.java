package universalmod.render.world.ambience;

import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import universalmod.module.render.Atmosphere;
import universalmod.render.Render3D;
import universalmod.render.gpu.*;

final class AtmosphereSkyRenderer {
    private static final GpuUniformBuffer DATA=new GpuUniformBuffer("AtmosphereSkyData",96);
    private static final RenderPipeline PIPELINE=new RenderPipeline("sky/sky","sky/atmosphere_dust_ash",
            () -> { },pass -> pass.setUniform("AtmosphereSkyData",DATA.buffer())).withBlend(false);
    private static final Matrix4f IDENTITY=new Matrix4f();
    private static final long EPOCH=System.currentTimeMillis();
    private static final float[][] WINTER={{.22f,.28f,.37f},{.70f,.77f,.85f},{.92f,.95f,1f},{.59f,.68f,.79f}};
    private static final float[][] SAKURA={{.69f,.80f,1f},{1f,.79f,.85f},{1f,.91f,.80f},{.94f,.73f,.81f}};
    private static final float[][] THUNDER={{.09f,.12f,.16f},{.21f,.23f,.26f},{.32f,.39f,.50f},{.24f,.26f,.29f}};
    private static final float[][] AUTUMN={{.37f,.42f,.48f},{.68f,.63f,.55f},{.92f,.72f,.44f},{.55f,.51f,.46f}};
    private static final float[][] SUNDAY={{.25f,.48f,.82f},{.77f,.88f,.98f},{1f,.87f,.63f},{.67f,.78f,.86f}};
    private static final float[][] BLOODMOON={{.04f,.04f,.07f},{.21f,.14f,.17f},{.65f,.10f,.10f},{.24f,.22f,.24f}};

    private AtmosphereSkyRenderer() { }
    static boolean render(Atmosphere atmosphere) {
        var client=MinecraftClient.getInstance();
        if(client.world==null || atmosphere==null || !(atmosphere.isMoodActive() || atmosphere.isWinterActive()))return false;
        float angle=client.world.getSkyAngleRadians(Render3D.lastTickDelta);
        var values=BufferUtils.createByteBuffer(96);
        IndexedGuiBatch.vec4(values,0,-(float)Math.sin(angle),(float)Math.cos(angle),0,(System.currentTimeMillis()-EPOCH)*.001f);
        float[][] palette=atmosphere.isWinterActive()?WINTER:atmosphere.isSakuraMode()?SAKURA:
                atmosphere.isThunderMode()?THUNDER:atmosphere.isAutumnMode()?AUTUMN:
                atmosphere.isSundayMode()?SUNDAY:BLOODMOON;
        for(int i=0;i<4;i++)IndexedGuiBatch.vec4(values,(i+1)*16,palette[i][0],palette[i][1],palette[i][2],0);
        IndexedGuiBatch.vec4(values,80,atmosphere.isWinterActive()?1:atmosphere.getMoodKind(),0,0,0);
        if(!DATA.write(values))throw new IllegalStateException("Atmosphere sky upload failed");
        GuiStateRenderer.getInstance().submitGuiElement(new SkyState());
        return true;
    }
    private record SkyState() implements GuiElementRenderState {
        @Override public void buildVertices(GuiVertexConsumer vertices) {
            float s=100;
            face(vertices,-s,-s,s, s,-s,s, s,s,s, -s,s,s);
            face(vertices,s,-s,-s, -s,-s,-s, -s,s,-s, s,s,-s);
            face(vertices,-s,-s,-s, -s,-s,s, -s,s,s, -s,s,-s);
            face(vertices,s,-s,s, s,-s,-s, s,s,-s, s,s,s);
            face(vertices,-s,s,s, s,s,s, s,s,-s, -s,s,-s);
            face(vertices,-s,-s,-s, s,-s,-s, s,-s,s, -s,-s,s);
        }
        private static void face(GuiVertexConsumer v,float... xyz) {
            for(int i=0;i<xyz.length;i+=3)v.addVertex(IDENTITY,xyz[i],xyz[i+1],xyz[i+2]).setColor(0xFFFFFFFF);
        }
        @Override public RenderPipeline pipeline() { return PIPELINE; }
        @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        @Override public ScreenRectangle scissorArea() { return null; }
        @Override public ScreenRectangle bounds() { return new ScreenRectangle(0,0,1,1); }
    }
    static void close() { DATA.close(); PIPELINE.close(); }
}
