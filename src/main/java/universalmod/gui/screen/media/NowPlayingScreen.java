package universalmod.gui.screen.media;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import universalmod.render.Render2D;
import universalmod.render.font.FontType;
import universalmod.render.media.MediaBackgroundRenderer;
import universalmod.render.media.MediaCoverShadowRenderer;
import universalmod.service.language.LanguageManager;
import universalmod.service.media.ArtworkPalette;
import universalmod.service.media.LyricsTimeline;
import universalmod.service.media.MusicTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class NowPlayingScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final String ICONS = "universalmod:textures/ui/media/";
    private static final long LYRIC_MANUAL_HOLD = 3500L;

    private final MusicTracker music;
    private final long openedAt = System.currentTimeMillis();
    private final List<Block> blocks = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private long closingAt = -1L;
    private long lastFrame;
    private float energy, phase, coverScale = 1f;
    private float lyricScroll = Float.NaN, lyricVelocity, manualOffset;
    private long manualUntil;
    private LyricsTimeline.Document layoutDocument;
    private float layoutWidth = Float.NaN, layoutSize = Float.NaN;
    private float blocksHeight;
    private float[] emphasis = new float[0];
    private float seekHover, volumeHover;
    private boolean seekDrag, volumeDrag;
    private float seekPreview;
    private float mouseX, mouseY;
    private Geometry geometry;

    public NowPlayingScreen(MusicTracker tracker) {
        super(Component.literal("Now Playing"));
        this.music = java.util.Objects.requireNonNull(tracker, "tracker");
        music.ensureStarted();
    }

    @Override public boolean isPauseScreen() { return false; }
@Override
    public void render(GuiGraphics graphics, int mouseGuiX, int mouseGuiY, float delta) {
        long now = System.currentTimeMillis();
        if (closingAt >= 0 && now - closingAt >= 240L) {
            if (this.minecraft != null) this.minecraft.setScreen(null);
            return;
        }
        music.ensureStarted();
        float dt = lastFrame == 0 ? .016f : Math.min(.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float visible = visibility(now);
        float sw = Render2D.getFixedScaledWidth(), sh = Render2D.getFixedScaledHeight();
        mouseX = (float) Render2D.guiToFixed(mouseGuiX);
        mouseY = (float) Render2D.guiToFixed(mouseGuiY);
        geometry = Geometry.of(sw, sh);
        updateAmbient(dt);

        Render2D.beginFrame(graphics);
        try { Render2D.rect(0, 0, sw, sh, rgba(0xFF08080A, visible)); }
        finally { Render2D.flush(); }
        boolean shader = MediaBackgroundRenderer.render(sw, sh, music.getArtworkPalette(), energy, phase, visible);

        Render2D.beginFrame(graphics);
        try { drawBackdrop(sw, sh, visible, now, shader); }
        finally { Render2D.flush(); }

        if (music.haveActiveSession()) {
            float anim=appear(now,40,700);
            float cover=geometry.coverSide();
            float scale=coverScale*(.82f+.18f*anim);
            float side=cover*scale;
            float x=geometry.coverCx()-side*.5f;
            float y=geometry.coverY()+cover*.5f+(1f-anim)*30f-side*.5f;
            MediaCoverShadowRenderer.render(sw,sh,x,y,side,
                    Math.max(4f,cover*.03f),cover*.05f,cover*.16f,
                    .45f*visible*clamp(anim)*coverScale);
        }
        Render2D.beginFrame(graphics);
        try {
            drawClose(visible);
            if (!music.haveActiveSession()) {
                String empty = localized("gui.music.nothing", "No music playing");
                textCentered(empty, sw*.5f, sh*.5f-8, 14f, rgba(WHITE, .7f*visible), FontType.BOLD);
                hits.clear();
                return;
            }
            drawNowPlaying(geometry, now, dt, visible);
            drawLyrics(geometry, now, dt, visible);
        } finally { Render2D.flush(); }
    }

    private float visibility(long now) {
        float t = clamp((now - openedAt)/360f);
        float value = 1f - (1f-t)*(1f-t)*(1f-t);
        if (closingAt >= 0) {
            float exit = clamp((now - closingAt)/240f);
            value *= 1f-exit*exit;
        }
        return value;
    }

    private float appear(long now, float delay, float duration) {
        float t = clamp((now - openedAt - delay)/duration);
        float q = t - 1f;
        return 1f + 2.4f*q*q*q + 1.4f*q*q;
    }

    private void updateAmbient(float dt) {
        float level = music.getAudioPeak();
        float db = 20f * (float)Math.log10(Math.max(0.0001f, level));
        float target = music.isPlaying() && level >= 0 ? clamp((db + 36f)/34f) : 0f;
        energy += (target-energy)*Math.min(1f, dt*1.5f);
        phase += dt*(music.isPlaying()?1f:.35f);
    }

    private void drawBackdrop(float sw, float sh, float alpha, long now, boolean shader) {
        ArtworkPalette p = music.getArtworkPalette();

        Identifier image = music.getBackgroundImage();
        Identifier previous = music.getPreviousBackgroundImage();
        float fade = music.getBackgroundTransition();
        if (image != null) {
            if (previous != null && fade < 1f) drawSwirl(previous,sw,sh,.5f*alpha*(1f-fade));
            drawSwirl(image,sw,sh,.5f*alpha*(previous == null ? 1f:fade));
        }

        Render2D.rect(0,0,sw,sh,0,
                rgba(BLACK,.25f*alpha),rgba(BLACK,.45f*alpha),
                rgba(BLACK,.50f*alpha),rgba(BLACK,.35f*alpha));
    }

    private void drawSwirl(Identifier id, float width, float height, float alpha) {
        if (alpha <= .01f) return;
        float max = Math.max(width,height);
        float x1=width*.30f+(float)Math.sin(phase*.05f)*width*.05f;
        float y1=height*.45f+(float)Math.cos(phase*.04f)*height*.05f;
        float x2=width*.75f+(float)Math.cos(phase*.045f)*width*.06f;
        float y2=height*.60f+(float)Math.sin(phase*.035f)*height*.06f;
        rotatedImage(id,x1,y1,max*1.9f,(float)phase*1.6f,alpha);
        rotatedImage(id,x2,y2,max*1.5f,140f-(float)phase*2.2f,alpha*.7f);
    }

    private static void rotatedImage(Identifier image,float cx,float cy,float side,float degrees,float a) {
        Render2D.image(image.toString(),cx-side/2f,cy-side/2f,side,0,degrees,cx,cy,rgba(WHITE,a));
    }

    private void drawClose(float alpha) {
        Rect r=new Rect(10,10,18,18);
        if (r.hit(mouseX,mouseY)) Render2D.rect(r.x(),r.y(),r.w(),r.h(),9,rgba(WHITE,.14f*alpha));
        MediaGlyphs.close(r.centerX(),r.centerY(),9f,rgba(WHITE,alpha*(r.hit(mouseX,mouseY)?1f:.75f)));
    }

    private void drawNowPlaying(Geometry g,long now,float dt,float alpha) {
        float centerX=g.coverCx(), coverSide=g.coverSide(), y=g.coverY(), left= centerX-coverSide/2f;
        float anim=appear(now,40,700), titleAnim=appear(now,150,600), controlsAnim=appear(now,240,600);
        float coverAlpha=alpha*clamp(anim), textAlpha=alpha*clamp(titleAnim), controlAlpha=alpha*clamp(controlsAnim);
        float targetScale=music.isPlaying()?1f:.88f;
        coverScale+=(targetScale-coverScale)*Math.min(1,dt*6f);
        float side=coverSide*coverScale*(.82f+.18f*anim);
        float cx=centerX, cy=y+coverSide*.5f+(1f-anim)*30f;
        float picX=cx-side*.5f, picY=cy-side*.5f;
        float coverRadius=Math.max(4f,coverSide*.03f);
        Identifier current=music.getImage(), previous=music.getPreviousImage();
        float cross=clamp(music.getArtworkTransition());
        if (current != null) {
            if (previous!=null && cross<1f) Render2D.image(previous.toString(),picX,picY,side,side,coverRadius*.5f,rgba(WHITE,coverAlpha*(1f-cubicOut(cross))));
            Render2D.image(current.toString(),picX,picY,side,side,coverRadius*.5f,
                    rgba(WHITE,coverAlpha*(previous!=null?cubicOut(cross):1f)));
        } else {
            Render2D.rect(picX,picY,side,side,coverRadius,rgba(WHITE,.1f*coverAlpha));
            MediaGlyphs.playPause(cx, cy, side*.24f, false, rgba(WHITE,coverAlpha*.4f));
        }

        float infoY=y+coverSide+11f+(1f-titleAnim)*16f;
        String title=music.title(), author=music.author();

        boolean hovered=mouseX>=left&&mouseX<=left+coverSide
                &&mouseY>=infoY-2&&mouseY<=infoY+20;
        metadata(title,left,infoY,coverSide,8f,rgba(WHITE,.95f*textAlpha),FontType.SEMIBOLD,hovered);
        metadata(author,left,infoY+10.5f,coverSide,7.5f,rgba(WHITE,.60f*textAlpha),FontType.DEFAULT,hovered);

        float barY=infoY+30f+(1f-controlsAnim)*18f;
        float barX=centerX-coverSide*.5f, barW=coverSide;
        float fraction=music.durationSeconds()>0 ? clamp((float)(music.getSyncedPositionMillis()/(music.durationSeconds()*1000))):0f;
        if (seekDrag) seekPreview=clamp((mouseX-barX)/barW);
        Rect seek=new Rect(barX-3f,barY-5f,barW+6f,11f);
        seekHover+=( ((seek.hit(mouseX,mouseY)||seekDrag)?1f:0f)-seekHover)*Math.min(1f,dt*12f);
        slider(barX,barY,barW,seekDrag?seekPreview:fraction,2.2f+1.4f*seekHover,5f+2f*seekHover,controlAlpha);
        long position=seekDrag ? (long)(seekPreview*music.durationSeconds()*1000):music.getSyncedPositionMillis();
        String elapsed=time(position), remaining="-"+time(Math.max(0,Math.round(music.durationSeconds()*1000)-position));
        text(elapsed,barX,barY+5f,5.2f,rgba(WHITE,.45f*controlAlpha),FontType.DEFAULT);
        text(remaining,barX+barW-textWidth(remaining,5.2f,FontType.DEFAULT),barY+5f,
                5.2f,rgba(WHITE,.45f*controlAlpha),FontType.DEFAULT);

        float buttonsY=barY+27f;
        float sideDistance=Math.min(34f,coverSide*.13f);
        g.seek=seek;
        g.play=square(centerX,buttonsY,24f);
        g.previous=square(centerX-sideDistance,buttonsY,20f);
        g.next=square(centerX+sideDistance,buttonsY,20f);
        g.shuffle=music.isShuffleSupported()?square(centerX-2*sideDistance,buttonsY,16f):null;
        g.repeat=music.isRepeatSupported()?square(centerX+2*sideDistance,buttonsY,16f):null;
        controlHover(g.previous,controlAlpha);
        controlHover(g.play,controlAlpha);
        controlHover(g.next,controlAlpha);
        MediaGlyphs.skip(centerX-sideDistance,buttonsY,13f,false,rgba(WHITE,.92f*controlAlpha));
        MediaGlyphs.playPause(centerX,buttonsY,15f,music.isPlaying(),rgba(WHITE,controlAlpha));
        MediaGlyphs.skip(centerX+sideDistance,buttonsY,13f,true,rgba(WHITE,.92f*controlAlpha));
        if(g.shuffle!=null){
            controlHover(g.shuffle,controlAlpha);
            MediaGlyphs.shuffle(centerX-2f*sideDistance,buttonsY,8f,
                    rgba(WHITE,controlAlpha*(music.isShuffleEnabled()?1f:.5f)));
        }
        if(g.repeat!=null){
            controlHover(g.repeat,controlAlpha);
            MediaGlyphs.repeat(centerX+2f*sideDistance,buttonsY,8.5f,
                    music.getRepeatMode()==1,rgba(WHITE,controlAlpha*(music.getRepeatMode()!=0?1f:.5f)));
        }

        float volY=buttonsY+25f,volX=barX+12f,volW=Math.max(12f,barW-12f);
        float volume=music.volume();
        g.volume=new Rect(volX-3f,volY-5f,volW+6f,10f);
        if (volumeDrag) music.setVolume(clamp((mouseX-volX)/volW));
        volumeHover+=(((g.volume.hit(mouseX,mouseY)||volumeDrag)?1f:0f)-volumeHover)*Math.min(1,dt*12f);
        int speakerWaves=volume<.02f?0:volume<.5f?1:2;
        MediaGlyphs.speaker(barX+3.5f,volY,7f,speakerWaves,rgba(WHITE,.60f*controlAlpha));
        slider(volX,volY,volW,Math.max(0,volume),2.2f+1.2f*volumeHover,5f+2f*volumeHover,controlAlpha);
    }

    private void controlHover(Rect r,float a) {
        if(r!=null&&r.hit(mouseX,mouseY))
            Render2D.rect(r.x(),r.y(),r.w(),r.h(),r.w()*.5f,rgba(WHITE,.10f*a));
    }

    private void slider(float x,float y,float w,float v,float thick,float knob,float a) {
        float p=clamp(v);
        Render2D.rect(x,y-thick*.5f,w,thick,thick*.5f,rgba(WHITE,.22f*a));
        Render2D.rect(x,y-thick*.5f,Math.max(0f,w*p),thick,thick*.5f,rgba(WHITE,.82f*a));
        Render2D.rect(x+w*p-knob*.5f,y-knob*.5f,knob,knob,knob*.5f,rgba(WHITE,a));
    }

    private void drawLyrics(Geometry g,long now,float dt,float alpha) {
        hits.clear();
        float x=g.lyricsX(),y=g.lyricsY(),w=g.lyricsW(),h=g.lyricsH();
        LyricsTimeline.Document doc=music.getLyricsDocument();
        float size=Math.max(15,Math.min(34,h*.058f));
        if(doc==null||doc.isEmpty()) {
            String msg=music.isLyricsLoading()?localized("gui.music.searching","Searching lyrics..."):
                    "Instrumental".equals(music.getLyricsStatus())?localized("gui.music.instrumental","Instrumental"):
                            localized("gui.music.noLyrics","No lyrics");
            text(msg,x,y+h*.42f,size*.8f,rgba(WHITE,.5f*alpha),FontType.BOLD);
            if(!music.isLyricsLoading() && !"Instrumental".equals(music.getLyricsStatus())) {
                text(localized("gui.music.source",""),x,y+h*.42f+size*.85f+4,6.5f,rgba(WHITE,.3f*alpha),FontType.DEFAULT);
            }
            return;
        }
        if(doc!=layoutDocument||Math.abs(layoutWidth-w)>.5f||Math.abs(layoutSize-size)>.1f) {
            layoutBlocks(doc,w,size);
            layoutDocument=doc;layoutWidth=w;layoutSize=size;
            lyricScroll=Float.NaN;lyricVelocity=0f;manualOffset=0f;
        }
        long position=music.getLyricsPositionMillis();

        int active=-1;
        if(doc.hasLineTiming()){
            for(int i=0;i<blocks.size();i++){
                if(blocks.get(i).timeMs()<=position)active=i;
                else break;
            }
        }
        float target=active>=0 ?blocks.get(active).top(): doc.hasLineTiming()?-size*2.5f:0;
        float anchor=y+h*(active>=0||doc.hasLineTiming()?.36f:.12f);
        if(now>manualUntil)manualOffset+=-manualOffset*Math.min(1,dt*4f);
        target+=manualOffset;
        if(Float.isNaN(lyricScroll))lyricScroll=target;
        float stiffness=90f,damping=2f*(float)Math.sqrt(stiffness)*.95f;
        lyricVelocity+=(stiffness*(target-lyricScroll)-damping*lyricVelocity)*dt;
        lyricScroll+=lyricVelocity*dt;
        if(emphasis.length!=blocks.size())emphasis=new float[blocks.size()];
        float rowHeight=size*1.08f;
        Render2D.pushScissor(Render2D.currentGraphics(),Math.max(0,x-30),Math.max(0,y),
                Math.min(g.sw(),w+70),Math.max(1,h));
        try {
            int shown=0;
            for(int i=0;i<blocks.size();i++) {
                Block block=blocks.get(i);
                float by=anchor+block.top()-lyricScroll;
                if(by+block.height()<y-20||by>y+h+20)continue;
                boolean selected=i==active;
                int distance=active>=0?i-active:2;
                emphasis[i]+=((selected?1f:0f)-emphasis[i])*Math.min(1f,dt*10f);
                float strength=emphasis[i];
                float stagger=clamp(appear(now,200+shown*55f,700));
                shown++;
                float in=alpha*stagger;
                boolean hover=mouseX>=x-8f&&mouseX<=x+w&&mouseY>=by-4f&&mouseY<=by+block.height()+4f;
                if(doc.hasLineTiming()){
                    Rect hit=new Rect(x-8,by-4,w+8,block.height()+8);
                    if(!block.dots())hits.add(new Hit(hit,block.timeMs()));
                    if(hover&&!block.dots())Render2D.rect(x-8,by-5,w+8,block.height()+10,8,rgba(WHITE,.08f*in));
                }

                float baseOpacity;
                if(!doc.hasLineTiming())baseOpacity=.75f;
                else if(distance<0)baseOpacity=Math.max(.05f,.22f+distance*.06f);
                else baseOpacity=Math.max(.12f,.34f-distance*.05f);
                float opacity=baseOpacity+(1f-baseOpacity)*strength;
                if(hover&&doc.hasLineTiming())opacity=Math.max(opacity,.7f);
                float edgeFade=clamp(.15f+Math.min(by-y,y+h-(by+block.height()))/(size*3f));
                opacity*=edgeFade*in;
                float blur=doc.hasLineTiming()&&!hover
                        ? (1f-strength)*Math.min(1f,.45f+Math.abs(distance)*.18f)*size*.09f :0f;
                float textSize=size*(.96f+.04f*strength);
                float rise=(1f-stagger)*50f;
                if(block.dots()){
                    drawDots(x,by+rise+block.height()/2f,block.height(),i==active,now,opacity);
                    continue;
                }
                for(int j=0;j<block.rows().size();j++) {
                    String row=block.rows().get(j);
                    float ry=by+rise+j*rowHeight;
                    if(ry+rowHeight<y||ry>y+h)continue;
                    softText(row,x,ry,textSize,opacity,blur);

                    if(selected&&doc.hasRealWordTiming()) {
                        LyricsTimeline.Line line=doc.lines().get(block.index());
                        float revealed=LyricsTimeline.revealedCodePoints(line,position);
                        for(int before=0;before<j;before++) {
                            String preceding=block.rows().get(before);
                            revealed-=preceding.codePointCount(0,preceding.length())+1;
                        }
                        if(revealed>0) {
                            float pixel=revealPixels(row,textSize,revealed);
                            Render2D.pushScissor(Render2D.currentGraphics(),Math.max(0,x),Math.max(0,ry-3),Math.max(.01f,pixel),rowHeight+6);
                            try { text(row,x,ry,textSize,rgba(WHITE,in),FontType.BOLD); }
                            finally {Render2D.popScissor(Render2D.currentGraphics());}
                        }
                    }
                }
            }
            if(doc.hasLineTiming()&& !blocks.isEmpty() && active<0 && position<blocks.get(0).timeMs()) {
                drawDots(x,anchor-lyricScroll-size*2.5f,size,false,now,alpha*.5f);
            }
        } finally {Render2D.popScissor(Render2D.currentGraphics());}
    }

    private static void softText(String value,float x,float y,float size,float opacity,float blur){
        if(opacity<=.01f)return;
        if(blur<.25f){text(value,x,y,size,rgba(WHITE,opacity),FontType.BOLD);return;}
        for(int ring=1;ring<=2;ring++){
            float radius=blur*ring/2f;
            for(int i=0;i<6;i++){
                double a=Math.PI*2d*i/6d+ring*.5d;
                text(value,x+(float)Math.cos(a)*radius,y+(float)Math.sin(a)*radius,
                        size,rgba(WHITE,opacity*.2f),FontType.BOLD);
            }
        }
        text(value,x,y,size,rgba(WHITE,opacity*.25f),FontType.BOLD);
    }

    private void drawDots(float x,float y,float height,boolean active,long now,float alpha) {
        float base=height*.26f;
        for(int i=0;i<3;i++){
            float wave=active?.5f+.5f*(float)Math.sin(now/280d-i*.9d):0f;
            float sz=base*(1f+.3f*wave);
            Render2D.circle(x+i*base*1.9f+(base-sz)*.5f+sz*.5f,
                    y,sz,rgba(WHITE,alpha*(active?.5f+.5f*wave:.4f)));
        }
    }

    private void layoutBlocks(LyricsTimeline.Document doc,float width,float size){
        blocks.clear();float cursor=0;
        float fontHeight=size*1.08f,gap=size*.85f;
        for(int i=0;i<doc.lines().size();i++){
            LyricsTimeline.Line line=doc.lines().get(i);
            String value=line.text()==null?"":line.text().trim();
            boolean blank=value.isEmpty();
            if(blank&&doc.hasLineTiming()){
                long next=i+1<doc.lines().size()?doc.lines().get(i+1).startMs():line.startMs()+10000;
                if(next-line.startMs()<4000)continue;
            }
            if(blank&&!doc.hasLineTiming()) {cursor+=gap*.5f;continue;}
            List<String> rows=blank?List.of():wrap(value,width,size);
            float height=blank?size*.6f:(rows.size()-1)*fontHeight+size;
            blocks.add(new Block(i,line.startMs(),rows,cursor,height,blank));
            cursor+=height+gap;
        }
        blocksHeight=cursor;
    }

    private static List<String> wrap(String value,float width,float size){
        if(value==null||value.isBlank())return List.of("");
        List<String> rows=new ArrayList<>();StringBuilder line=new StringBuilder();
        for(String part:value.split("\\s+")){
            String candidate=line.isEmpty()?part:line+" "+part;
            if(!line.isEmpty()&&textWidth(candidate,size,FontType.BOLD)>width){
                rows.add(line.toString());line.setLength(0);
            }
            if(!line.isEmpty())line.append(' ');
            line.append(part);
        }
        if(!line.isEmpty())rows.add(line.toString());
        return rows.isEmpty()?List.of(""):List.copyOf(rows);
    }

    private static float revealPixels(String text,float size,float visible){
        int length=text.codePointCount(0,text.length());
        if(visible>=length)return textWidth(text,size,FontType.BOLD);
        int count=Math.max(0,Math.min(length,(int)Math.floor(visible)));
        int index=text.offsetByCodePoints(0,count);
        float previous=textWidth(text.substring(0,index),size,FontType.BOLD);
        if(count==length)return previous;
        int next=text.offsetByCodePoints(index,1);
        return previous+(textWidth(text.substring(0,next),size,FontType.BOLD)-previous)*(visible-count);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled){
        if(event.button()!=0||geometry==null)return super.mouseClicked(event,doubled);
        float px=(float)Render2D.guiToFixed(event.x()),py=(float)Render2D.guiToFixed(event.y());
        if(new Rect(10,10,18,18).hit(px,py)){onClose();return true;}
        if(!music.haveActiveSession())return super.mouseClicked(event,doubled);
        Geometry g=geometry;
        if(hit(g.seek,px,py)&&music.durationSeconds()>0){
            seekDrag=true;seekPreview=clamp((px-(g.seek.x()+3))/(g.seek.w()-6));return true;
        }
        if(hit(g.volume,px,py)&&music.volume()>=0){
            volumeDrag=true;music.setVolume(clamp((px-(g.volume.x()+3))/(g.volume.w()-6)));return true;
        }
        if(hit(g.play,px,py)){music.togglePlay();return true;}
        if(hit(g.previous,px,py)){music.previousTrack();return true;}
        if(hit(g.next,px,py)){music.nextTrack();return true;}
        if(hit(g.shuffle,px,py)){if(music.isShuffleSupported())music.toggleShuffle();return true;}
        if(hit(g.repeat,px,py)){if(music.isRepeatSupported())music.cycleRepeat();return true;}
        for(Hit lyric:hits)if(lyric.box().hit(px,py)){
            music.seekToMillis(Math.max(0,lyric.timeMs()-music.getLyricsOffsetMillis()-50));return true;
        }
        return super.mouseClicked(event,doubled);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx,double dy){
        if(event.button()==0&&geometry!=null){
            float px=(float)Render2D.guiToFixed(event.x());
            if(seekDrag){seekPreview=clamp((px-(geometry.seek.x()+3))/(geometry.seek.w()-6));return true;}
            if(volumeDrag){music.setVolume(clamp((px-(geometry.volume.x()+3))/(geometry.volume.w()-6)));return true;}
        }
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(event.button()==0&&seekDrag){seekDrag=false;music.seekToMillis(Math.round(seekPreview*music.durationSeconds()*1000));return true;}
        if(event.button()==0&&volumeDrag){volumeDrag=false;return true;}
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(geometry==null)return super.mouseScrolled(x,y,horizontal,vertical);
        float px=(float)Render2D.guiToFixed(x),py=(float)Render2D.guiToFixed(y);

        Geometry g=geometry;
        if(g.leftPanel().hit(px,py)){music.adjustVolume((float)Math.signum(vertical)*.05f);return true;}
        if(!blocks.isEmpty()){
            manualOffset=clampRange(manualOffset-(float)vertical*layoutSize*2,-blocksHeight,blocksHeight);
            manualUntil=System.currentTimeMillis()+LYRIC_MANUAL_HOLD;
            return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyEvent event){
        switch(event.key()){
            case GLFW.GLFW_KEY_SPACE -> {music.togglePlay();return true;}
            case GLFW.GLFW_KEY_LEFT -> {music.seekToMillis(music.getSyncedPositionMillis()-5000);return true;}
            case GLFW.GLFW_KEY_RIGHT -> {music.seekToMillis(music.getSyncedPositionMillis()+5000);return true;}
            case GLFW.GLFW_KEY_UP -> {music.adjustVolume(.05f);return true;}
            case GLFW.GLFW_KEY_DOWN -> {music.adjustVolume(-.05f);return true;}
        }
        return super.keyPressed(event);
    }
    @Override public void onClose(){if(closingAt<0)closingAt=System.currentTimeMillis();}

    private static void icon(String name,float x,float y,float side,float alpha){
        Render2D.image(ICONS+name+".png",x,y,side,side,0,rgba(WHITE,alpha));
    }

    private void metadata(String value,float x,float y,float width,float size,int color,FontType font,boolean hover){
        if(value==null||value.isEmpty())return;
        float full=textWidth(value,size,font);
        if(full<=width){text(value,x,y,size,color,font);return;}
        if(!hover){text(ellipsize(value,width,size,font),x,y,size,color,font);return;}
        float travel=full+30f;
        long cycleMs=(long)(travel/30f*1000f);
        long time=Math.floorMod(System.currentTimeMillis(),Math.max(1L,cycleMs+1200L));
        float scroll=time<1200L?0f:((time-1200L)/(float)Math.max(1L,cycleMs))*travel;
        Render2D.pushScissor(Render2D.currentGraphics(),Math.max(0,x),Math.max(0,y-2),Math.max(1,width),size+6);
        try{
            text(value,x-scroll,y,size,color,font);
            text(value,x-scroll+travel,y,size,color,font);
        }finally{Render2D.popScissor(Render2D.currentGraphics());}
    }
    private static String ellipsize(String value,float width,float size,FontType font){
        if(textWidth(value,size,font)<=width)return value;
        int codePoints=value.codePointCount(0,value.length());
        int low=0,high=codePoints;
        while(low<high){
            int mid=(low+high+1)>>>1;
            String candidate=value.substring(0,value.offsetByCodePoints(0,mid))+"…";
            if(textWidth(candidate,size,font)<=width)low=mid;
            else high=mid-1;
        }
        return value.substring(0,value.offsetByCodePoints(0,low))+"…";
    }
    private static void text(String s,float x,float y,float size,int c,FontType font){
        if(s!=null&&!s.isEmpty())Render2D.text(font,s,x,y,size,c);
    }
    private static void textCentered(String s,float x,float y,float size,int c,FontType font){
        text(s,x-textWidth(s,size,font)*.5f,y,size,c,font);
    }
    private static float textWidth(String s,float size,FontType font){
        return Render2D.textWidth(font,s==null?"":s,size);
    }
    private static String trim(String value,float max,float size){
        if(value==null)return "";
        if(textWidth(value,size,FontType.SEMIBOLD)<=max)return value;
        String temp=value;
        while(!temp.isEmpty()&&textWidth(temp+"…",size,FontType.SEMIBOLD)>max){
            temp=temp.substring(0,temp.offsetByCodePoints(temp.length(),-1));
        }
        return temp+"…";
    }
    private static String localized(String key,String fallback){
        String value=LanguageManager.ui(key);
        return Objects.equals(value,key)?fallback:value;
    }
    private static int rgba(int argb,float alpha){
        return Math.round(((argb>>>24)&255)*clamp(alpha))<<24 | (argb&0xFFFFFF);
    }
    private static float clamp(float x){return Math.max(0f,Math.min(1f,x));}
    private static float clampRange(float x,float a,float b){return Math.max(a,Math.min(b,x));}
    private static float cubicOut(float x){float y=1f-clamp(x);return 1f-y*y*y;}
    private static String time(long ms){long seconds=Math.max(0,ms/1000);return seconds/60+":"+String.format(Locale.ROOT,"%02d",seconds%60);}
    private static Rect square(float centerX,float centerY,float side){return new Rect(centerX-side/2,centerY-side/2,side,side);}
    private static boolean hit(Rect rect,float x,float y){return rect!=null&&rect.hit(x,y);}

    private record Rect(float x,float y,float w,float h){
        float centerX(){return x+w/2f;}
        float centerY(){return y+h/2f;}
        boolean hit(float px,float py){return px>=x&&py>=y&&px<x+w&&py<y+h;}
    }
    private record Block(int index,long timeMs,List<String> rows,float top,float height,boolean dots){}
    private record Hit(Rect box,long timeMs){}
    private static final class Geometry {
        private final float sw,sh,coverCx,coverY,coverSide,lyricsX,lyricsY,lyricsW,lyricsH;
        private Rect seek,volume,play,previous,next,shuffle,repeat,leftPanel;
        private Geometry(float sw,float sh,float cx,float cy,float side,float lx,float ly,float lw,float lh){
            this.sw=sw;this.sh=sh;coverCx=cx;coverY=cy;coverSide=side;
            lyricsX=lx;lyricsY=ly;lyricsW=lw;lyricsH=lh;
            leftPanel=new Rect(cx-side/2-10,cy-10,side+20,side+140);
        }
        static Geometry of(float sw,float sh){
            if(sw>sh*1.15f){
                float cover=Math.min(sh*.56f,sw*.29f);
                float cy=Math.max(26f,(sh-(cover+124f))/2f-6f);
                return new Geometry(sw,sh,sw*.28f,cy,cover,sw*.49f,0,sw*.40f,sh);
            }
            float cover=Math.min(sh*.30f,sw*.60f);
            float cy=30f,lyricsY=cy+cover+130f;
            return new Geometry(sw,sh,sw*.5f,cy,cover,sw*.10f,lyricsY,sw*.80f,Math.max(0,sh-lyricsY));
        }
        float sw(){return sw;}float sh(){return sh;}
        float coverCx(){return coverCx;}float coverY(){return coverY;}float coverSide(){return coverSide;}
        float lyricsX(){return lyricsX;}float lyricsY(){return lyricsY;}
        float lyricsW(){return lyricsW;}float lyricsH(){return lyricsH;}
        Rect leftPanel(){return leftPanel;}
    }
}
