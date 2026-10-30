package universalmod.render.world;

public final class WorldLyricsAnimation {
    private WorldLyricsAnimation() {}

    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public static float smooth(float value) {
        float t = clamp01(value);
        return t * t * (3.0F - 2.0F * t);
    }

    public static float linePresence(long positionMs, long startMs, long endMs, int fadeMs) {
        long end = Math.max(startMs + 1L, endMs);
        int fade = Math.max(1, fadeMs);
        float enter = smooth((positionMs - startMs + 100.0F) / fade);
        float leave = 1.0F - smooth((positionMs - end + fade * 0.80F) / fade);
        return clamp01(enter * leave);
    }

    public static float glyphFill(float completedCharacters, int glyphIndex) {

        return smooth((completedCharacters - glyphIndex + 0.30F) / 1.15F);
    }

    public static float wordAppearing(long currentMs, long wordStartMs, int fadeMs, long leadMs) {
        int transition = Math.max(60, fadeMs);
        return smooth((currentMs - (wordStartMs - Math.max(0L, leadMs))) / (float) transition);
    }

    public static float wordReveal(long currentMs, long wordStartMs, long wordEndMs) {
        return smooth((currentMs - wordStartMs) / (float) Math.max(1L, wordEndMs - wordStartMs));
    }

}
