package universalmod.render.blur;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2f;


record BlurCaptureRequest(BlurPass blur, Matrix3x2f pose, ScreenRectangle scissorArea) {
}
