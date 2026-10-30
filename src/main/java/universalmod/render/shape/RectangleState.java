package universalmod.render.shape;

import org.joml.Matrix4f;

public record RectangleState(RectangleShape rectangle, Matrix4f pose) {
    public RectangleState {
        if (rectangle == null || pose == null) throw new IllegalArgumentException("Null rectangle submission");
        pose = new Matrix4f(pose);
    }
    @Override public Matrix4f pose() { return new Matrix4f(pose); }
}
