package universalmod.render.blur;

record BlurRegion(int x, int y, int width, int height) {
    static BlurRegion fromGui(float x, float y, float width, float height, float padding,
                              int guiWidth, int guiHeight, int frameWidth, int frameHeight) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
                || !Float.isFinite(padding) || padding < 0.0f || width <= 0 || height <= 0 || guiWidth <= 0 || guiHeight <= 0
                || frameWidth <= 0 || frameHeight <= 0) return null;
        float sx = frameWidth / (float) guiWidth;
        float sy = frameHeight / (float) guiHeight;

        int x0 = clamp((int) Math.floor((x - padding) * sx), 0, frameWidth - 1);
        int x1 = clamp((int) Math.ceil((x + width + padding) * sx), x0 + 1, frameWidth);
        int y0 = clamp((int) Math.floor((guiHeight - (y + height + padding)) * sy), 0, frameHeight - 1);
        int y1 = clamp((int) Math.ceil((guiHeight - (y - padding)) * sy), y0 + 1, frameHeight);
        return new BlurRegion(x0, y0, x1 - x0, y1 - y0);
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
