package universalmod.gui.screen.media;

import universalmod.render.Render2D;
import universalmod.render.font.FontType;

final class MediaGlyphs {
    private static final String PATH = "textures/ui/media/media_vectors/";
    private MediaGlyphs() {}

    static void playPause(float x,float y,float size,boolean isPlaying,int color) {
        if (isPlaying) {
            float w=size*.24f,h=size*.78f,gap=size*.20f;
            float radius=Math.min(w*.5f,size*.07f);
            Render2D.rect(x-gap*.5f-w,y-h*.5f,w,h,radius,color);
            Render2D.rect(x+gap*.5f,y-h*.5f,w,h,radius,color);
        } else {
            glyph("s",x+size*.06f,y,size*.95f,color);
        }
    }
    static void skip(float x,float y,float size,boolean next,int color) {
        String arrow=next?"z":"y";
        float glyphSize=size*.62f,spacing=size*.23f;
        glyph(arrow,x-spacing,y,glyphSize,color);
        glyph(arrow,x+spacing,y,glyphSize,color);
    }
    static void shuffle(float x,float y,float size,int color) {
        vector("shuffle_base",x,y,size,color);
        float rx=x+size*(.28f+.10f);
        glyph("z",rx,y-size*.30f,size*.42f,color);
        glyph("z",rx,y+size*.30f,size*.42f,color);
    }
    static void repeat(float x,float y,float size,boolean repeatOne,int color) {
        vector("repeat_base",x,y,size,color);
        float arrowX=size*(.86f*.14f);
        glyph("z",x+arrowX,y-size*.28f,size*.36f,color);
        glyph("y",x-arrowX,y+size*.28f,size*.36f,color);
        if(repeatOne) Render2D.text(FontType.SEMIBOLD,"1",x-size*.20f,y-size*.20f,size*.42f,color);
    }
    static void speaker(float x,float y,float size,int waves,int color) {
        vector("speaker_"+Math.max(0,Math.min(2,waves)),x,y,size,color);
        glyph("y",x-size*.42f+size*.20f+size*.08f,y,size*.66f,color);
    }
    static void close(float x,float y,float size,int color) {
        vector("close_base",x,y,size,color);
    }
    private static void vector(String name,float cx,float cy,float size,int color){
        Render2D.image(PATH+name+".png",cx-size,cy-size,size*2f,size*2f,0f,color);
    }

    private static String mediaGlyph(String g) {
        return switch (g) {
            case "s" -> "\uE220";
            case "y" -> "\uE221";
            case "z" -> "\uE222";
            default -> throw new IllegalArgumentException("Unknown media glyph: " + g);
        };
    }
    private static void glyph(String g,float x,float y,float size,int color) {
        Render2D.text(FontType.DELTA_ICONS,mediaGlyph(g),x-size*.5f,y-size*.5f,size,color);
    }
}
