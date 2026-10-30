package universalmod.access;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2f;
import java.util.function.Consumer;

public interface ChatGraphicsAccessInvoker {
    default void universalmod$updatePose(Consumer<Matrix3x2f> operation) {
        var matrix=((DrawContext)(Object)this).getMatrices().peek().getPositionMatrix();
        Matrix3x2f pose=new Matrix3x2f(matrix.m00(),matrix.m01(),matrix.m10(),matrix.m11(),matrix.m30(),matrix.m31());
        operation.accept(pose);
        matrix.m00(pose.m00()).m01(pose.m01()).m10(pose.m10()).m11(pose.m11()).m30(pose.m20()).m31(pose.m21());
    }
}
