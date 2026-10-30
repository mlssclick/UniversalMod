package universalmod.render.image;

public record ImageQuad(float x,float y,float width,float height,float u0,float v0,float u1,float v1,
                        int colorTopLeft,int colorTopRight,int colorBottomRight,int colorBottomLeft,
                        float radiusTopLeft,float radiusTopRight,float radiusBottomRight,float radiusBottomLeft,
                        float smoothness,float rotationDegrees,float rotationOriginX,float rotationOriginY) {
    public ImageQuad(float x,float y,float width,float height,float u0,float v0,float u1,float v1,
                     int tl,int tr,int br,int bl) {
        this(x,y,width,height,u0,v0,u1,v1,tl,tr,br,bl,0,0,0,0,1,0,x+width*.5f,y+height*.5f);
    }
    public float rotationCos() { return (float)Math.cos(Math.toRadians(rotationDegrees)); }
    public float rotationSin() { return (float)Math.sin(Math.toRadians(rotationDegrees)); }
}
