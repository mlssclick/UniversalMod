package universalmod.module.misc;

import org.lwjgl.glfw.GLFW;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BindSetting;
import universalmod.setting.NumberSetting;

@ModuleInformation(
        moduleName = "Zoom",
        moduleCategory = ModuleCategory.MISC
)
public final class Zoom extends Module {
    private static final double MIN_ZOOM_LEVEL = 1.0;
    private static final double MAX_ZOOM_LEVEL = 30.0;
    private static final double FALLBACK_ZOOM_LEVEL = 3.0;
    private static final double SENSITIVITY_EPSILON = 1.0E-4;

    private static Zoom instance;

    private final BindSetting zoomBind = new BindSetting("Zoom Bind", "Hold this key to zoom the camera.", universalmod.setting.bind.KeyBind.keyboard(GLFW.GLFW_KEY_V));
    private final NumberSetting defaultZoom = new NumberSetting(
            "Default Zoom",
            FALLBACK_ZOOM_LEVEL,
            MIN_ZOOM_LEVEL,
            MAX_ZOOM_LEVEL,
            0.1
    );

    private Double currentLevel;
    private Double defaultMouseSensitivity;
    private Double appliedSensitivity;

    public Zoom() {
        instance = this;
        defaultZoom.setDescription("Default zoom multiplier while holding the zoom key.");
        setEnabled(true);
    }

    public static Zoom getInstance() {
        return instance;
    }

    public double changeFovBasedOnZoom(double fov) {
        if (mc == null || mc.options == null) {
            return fov;
        }

        if (currentLevel == null) {
            currentLevel = defaultZoom.getValue();
        }
        currentLevel = clampZoomLevel(currentLevel);

        if (!isZooming()) {
            currentLevel = defaultZoom.getValue();
            restoreMouseSensitivity();
            return fov;
        }

        if (defaultMouseSensitivity == null) {
            defaultMouseSensitivity = mc.options.getMouseSensitivity().getValue();
        }

        double targetSensitivity = defaultMouseSensitivity / currentLevel;
        if (appliedSensitivity == null || Math.abs(appliedSensitivity - targetSensitivity) > SENSITIVITY_EPSILON) {
            mc.options.getMouseSensitivity().setValue(targetSensitivity);
            appliedSensitivity = targetSensitivity;
        }

        return fov / currentLevel;
    }

    public float changeFovBasedOnZoom(float fov) {
        return (float) changeFovBasedOnZoom((double) fov);
    }

     
    public double projectionFov(double baseFov) {
        double level = currentLevel == null ? defaultZoom.getValue() : currentLevel;
        return isZooming() ? baseFov / clampZoomLevel(level) : baseFov;
    }

    public void onMouseScroll(double amount) {
        if (mc == null || mc.currentScreen != null) return;
        double level = currentLevel == null ? defaultZoom.getValue() : currentLevel;
        if (amount > 0.0) {
            currentLevel = clampZoomLevel(level * 1.1);
        } else if (amount < 0.0) {
            currentLevel = clampZoomLevel(level * 0.9);
        }
    }

    public boolean isZoomHeld() {
        return isZooming();
    }

    private boolean isZooming() {
        if (!isEnabled() || mc == null || mc.getWindow() == null || mc.currentScreen != null) {
            return false;
        }

        return zoomBind.getValue().isDown(mc.getWindow().getHandle());
    }

    @Override
    public void onDisable() {
        restoreMouseSensitivity();
        currentLevel = null;
        super.onDisable();
    }

    private void restoreMouseSensitivity() {
        if (mc != null && mc.options != null && defaultMouseSensitivity != null) {
            mc.options.getMouseSensitivity().setValue(defaultMouseSensitivity);
        }
        defaultMouseSensitivity = null;
        appliedSensitivity = null;
    }

    private static double clampZoomLevel(Double value) {
        double safeValue = value != null && Double.isFinite(value) ? value : FALLBACK_ZOOM_LEVEL;
        return Math.max(MIN_ZOOM_LEVEL, Math.min(MAX_ZOOM_LEVEL, safeValue));
    }
}
