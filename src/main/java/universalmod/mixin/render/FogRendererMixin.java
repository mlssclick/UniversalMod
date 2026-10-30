package universalmod.mixin.render;

import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.module.render.Ambience;
import universalmod.module.render.Atmosphere;
import universalmod.module.render.NoRender;

@Mixin(BackgroundRenderer.class)
public abstract class FogRendererMixin {
    @Unique private static final float UNREACHABLE=1_000_000f;
    @Unique private static final float NO_FOG_START=1_000_000f;
    @Unique private static final float NO_FOG_END=1_000_001f;

    @Inject(method="getFogColor",at=@At("RETURN"),cancellable=true)
    private static void universalmod$replaceClearColor(Camera camera,float tickDelta,ClientWorld world,
            int viewDistance,float skyDarkness,CallbackInfoReturnable<Vector4f> cir) {
        Vector4f color=cir.getReturnValue();
        Ambience ambience=Ambience.getInstance();
        if(universalmod$isAtmosphereFog(camera) || ambience!=null && ambience.shouldApplyCustomFog(camera)) {
            color=universalmod$color(color==null?1:color.w);
            cir.setReturnValue(color);
        }
    }

    @Inject(method="applyFog",at=@At("RETURN"),cancellable=true)
    private static void universalmod$replaceFog(Camera camera,BackgroundRenderer.FogType type,Vector4f color,
            float viewDistance,boolean thick,float tickDelta,CallbackInfoReturnable<Fog> cir) {
        Ambience ambience=Ambience.getInstance();
        boolean custom=universalmod$isAtmosphereFog(camera) || ambience!=null && ambience.shouldApplyCustomFog(camera);
        if(!custom && !NoRender.shouldRemoveVanillaFog(camera))return;
        Fog vanilla=cir.getReturnValue();
        Vector4f result=custom?universalmod$color(color==null?1:color.w):color;
        if(result==null) result=vanilla==null?new Vector4f(0,0,0,1):new Vector4f(vanilla.red(),vanilla.green(),vanilla.blue(),vanilla.alpha());
        cir.setReturnValue(new Fog(custom?universalmod$fogStart():NO_FOG_START,
                custom?universalmod$fogDistance():NO_FOG_END,FogShape.SPHERE,result.x,result.y,result.z,result.w));
    }

    @Unique
    private static boolean universalmod$isAtmosphereFog(Camera camera) {
        Atmosphere atmosphere = Atmosphere.getInstance();
        return atmosphere != null && (atmosphere.shouldApplyWinterFog(camera));
    }

    @Unique
    private static int universalmod$fogRgb() {
        Atmosphere atmosphere = Atmosphere.getInstance();
        if (atmosphere != null && (atmosphere.isWinterActive())) return atmosphere.getFogColor();
        Ambience ambience = Ambience.getInstance();
        return ambience == null ? 0 : ambience.getCustomFogColor() & 0x00FFFFFF;
    }

    @Unique
    private static float universalmod$fogStart() {
        Atmosphere atmosphere = Atmosphere.getInstance();
        if (atmosphere != null && (atmosphere.isWinterActive())) return atmosphere.getFogStart();
        Ambience ambience = Ambience.getInstance();
        return ambience == null ? UNREACHABLE : ambience.getFogStart();
    }

    @Unique
    private static float universalmod$fogDistance() {
        Atmosphere atmosphere = Atmosphere.getInstance();
        if (atmosphere != null && (atmosphere.isWinterActive())) return atmosphere.getFogDistance();
        Ambience ambience = Ambience.getInstance();
        return ambience == null ? UNREACHABLE : ambience.getFogDistance();
    }

    @Unique
    private static Vector4f universalmod$color(float alpha) {
        int rgb = universalmod$fogRgb();
        return new Vector4f(((rgb >> 16) & 255) / 255.0F,
                ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F, alpha);
    }
}
