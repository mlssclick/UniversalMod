package universalmod.render.effect;

public record EffectIconQuad(float x,float y,float size,int color,float u0,float v0,float u1,float v1) {
    public EffectIconQuad(float x,float y,float size,int color) { this(x,y,size,color,0,0,1,1); }
}
