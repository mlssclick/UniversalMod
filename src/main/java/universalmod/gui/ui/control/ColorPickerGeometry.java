package universalmod.gui.ui.control;


public record ColorPickerGeometry(Bounds palette, Bounds hue, Bounds alpha) {
    public static ColorPickerGeometry clickGui(float popupX, float popupY, float scale) {
        float paletteX = popupX + 4.5F * scale;
        float paletteY = popupY + 4.5F * scale;
        float paletteWidth = 96.0F * scale;
        float paletteHeight = 62.0F * scale;
        Bounds palette = new Bounds(paletteX, paletteY, paletteWidth, paletteHeight);
        Bounds hue = new Bounds(paletteX, paletteY + paletteHeight + 4.5F * scale,
                paletteWidth, 5.5F * scale);
        Bounds alpha = new Bounds(paletteX, hue.bottom() + 4.0F * scale,
                paletteWidth, 5.5F * scale);
        return new ColorPickerGeometry(palette, hue, alpha);
    }
}
