package universalmod.event.type;

import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import universalmod.event.Event;

 
public record WorldLyricsRenderEvent(Matrix4f viewMatrix, Matrix4f projectionMatrix,
                                    Vec3d cameraPosition, Quaternionf cameraRotation,
                                    float tickDelta) implements Event {
}
