package universalmod.service.media;

public record ArtworkPalette(int primary, int secondary, int highlight, int foreground, int fourth) {
    public static final ArtworkPalette DEFAULT = new ArtworkPalette(
            0xFF7A5CFF, 0xFF3A2A6A, 0xFFB35CFF, 0xFFFFFFFF, 0xFF583A85);
}
