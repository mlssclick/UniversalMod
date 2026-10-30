package universalmod.module.render;

import universalmod.event.SubscribeEvent;
import universalmod.event.type.WorldRenderEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.render.world.WorldLyricsRenderer;
import universalmod.service.media.MusicTracker;
import universalmod.setting.BooleanSetting;
import universalmod.setting.NumberSetting;

 
public final class WorldLyrics extends Module {
    private final MusicTracker music;
    private final WorldLyricsRenderer renderer = new WorldLyricsRenderer();

    private final NumberSetting distance = register(new NumberSetting(
            "Distance", "How far the lyrics appear in front of the camera, in blocks.", 6.5D, 2.0D, 14.0D, 0.25D));
    private final NumberSetting height = register(new NumberSetting(
            "Vertical Offset", "Vertical placement within the view.", -0.35D, -2.5D, 2.5D, 0.05D));
    private final NumberSetting size = register(new NumberSetting(
            "Text Size", "World text size without affecting karaoke sync.", 100.0D, 50.0D, 180.0D, 5.0D));
    private final NumberSetting spread = register(new NumberSetting(
            "Word Spread", "Horizontal spread of the independent world-anchored words.", 100.0D, 65.0D, 150.0D, 5.0D));
    private final NumberSetting fade = register(new NumberSetting(
            "Transition", "Fade duration as words appear and the current lyric line ends (ms).", 360.0D, 120.0D, 850.0D, 10.0D));
    private final NumberSetting motion = register(new NumberSetting(
            "Motion", "Subtle local entrance movement without following the camera.", 55.0D, 0.0D, 100.0D, 5.0D));
    private final BooleanSetting stopAtWalls = register(new BooleanSetting(
            "Respect Walls", "Anchor each new word in front of intervening world geometry.", true));

    public WorldLyrics(MusicTracker music) {
        super("world_lyrics", "World Lyrics", ModuleCategory.RENDER);
        this.music = java.util.Objects.requireNonNull(music, "music");
    }

    @Override
    protected void onDisable() {
        renderer.reset();
    }

    @SubscribeEvent
    private void renderWorld(WorldRenderEvent event) {
        if (!isEnabled()) return;
        renderer.render(event, music, distance.getFloat(), height.getFloat(), size.getFloat() / 100.0F,
                spread.getFloat() / 100.0F, (int) Math.round(fade.getValue()), motion.getFloat() / 100.0F, stopAtWalls.getValue());
    }
}
