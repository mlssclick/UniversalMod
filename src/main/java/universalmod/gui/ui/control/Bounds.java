package universalmod.gui.ui.control;


public record Bounds(float x, float y, float width, float height) {
    public Bounds {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)) {
            throw new IllegalArgumentException("Bounds must be finite");
        }
        width = Math.max(0.0F, width);
        height = Math.max(0.0F, height);
    }

    public float right() { return x + width; }
    public float bottom() { return y + height; }
    public float centerX() { return x + width * 0.5F; }
    public float centerY() { return y + height * 0.5F; }

    public boolean contains(double px, double py) {
        return px >= x && px <= right() && py >= y && py <= bottom();
    }

    public Bounds inset(float amount) {
        float nextW = Math.max(0.0F, width - amount * 2.0F);
        float nextH = Math.max(0.0F, height - amount * 2.0F);
        return new Bounds(x + amount, y + amount, nextW, nextH);
    }

    public Bounds expanded(float amount) {
        return expanded(amount, amount);
    }

    public Bounds expanded(float horizontal, float vertical) {
        return new Bounds(x - horizontal, y - vertical,
                width + horizontal * 2.0F, height + vertical * 2.0F);
    }

    public Bounds translated(float dx, float dy) {
        return new Bounds(x + dx, y + dy, width, height);
    }
}
