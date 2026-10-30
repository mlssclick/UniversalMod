package universalmod.render.shape;

import org.joml.Matrix4f;

public record OutlineState(OutlineShape outline, Matrix4f pose) {
    public OutlineState {
        if (outline == null || pose == null) throw new IllegalArgumentException("Null outline submission");
        pose = new Matrix4f(pose);
    }
    @Override public Matrix4f pose() { return new Matrix4f(pose); }
}
