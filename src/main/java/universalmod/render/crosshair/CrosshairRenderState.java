package universalmod.render.crosshair;

import java.awt.Color;

public record CrosshairRenderState(boolean enabled, boolean displayInThirdPerson, boolean vanillaBlending,
                                   boolean dynamicColor, CrosshairCanvas canvas, Color crosshairColor,
                                   Color neutralColor, Color hostileColor, Color playersColor) {
}
