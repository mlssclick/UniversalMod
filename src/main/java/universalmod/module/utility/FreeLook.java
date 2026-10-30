package universalmod.module.utility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.MouseRotationEvent;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.setting.BindSetting;

@ModuleInformation(
        moduleName = "Free Look",
        moduleCategory = ModuleCategory.UTILS
)
public final class FreeLook extends Module {
    public final BindSetting freeLookBind = new BindSetting(
            "Free Look", "Free look key.",
            universalmod.setting.bind.KeyBind.keyboard(GLFW.GLFW_KEY_LEFT_ALT));

    private static FreeLook instance;
    private Perspective previousPerspective;
    private float cameraYaw;
    private float cameraPitch;
    private boolean active;

    public FreeLook() {
        instance = this;
    }

    public static FreeLook getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        deactivate();
        super.onDisable();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (isBindDown()) {
            activate();
        } else if (previousPerspective != null) {
            deactivate();
        }
    }

    @SubscribeEvent
    private void onMouseRotation(MouseRotationEvent event) {
        if (!isBindDown()) {
            active = false;
            return;
        }

        activate();
        cameraYaw += event.getCursorDeltaX() * 0.15F;
        cameraPitch = MathHelper.clamp(cameraPitch + event.getCursorDeltaY() * 0.15F, -90.0F, 90.0F);
        event.setCancelled(true);
    }

    public boolean isActive() {
        return isEnabled() && active && isBindDown();
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }

    private void activate() {
        if (mc.player == null || mc.options == null) return;
        if (previousPerspective == null) {
            previousPerspective = mc.options.getPerspective();
            cameraYaw = mc.gameRenderer.getCamera().getYaw();
            cameraPitch = mc.gameRenderer.getCamera().getPitch();
        }
        if (mc.options.getPerspective().isFirstPerson()) {
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
        active = true;
    }

    private void deactivate() {
        if (previousPerspective != null && mc.options != null) {
            mc.options.setPerspective(previousPerspective);
        }
        previousPerspective = null;
        active = false;
    }

    private boolean isBindDown() {
        return mc.currentScreen == null
                && mc.getWindow() != null
                && freeLookBind.getValue().isDown(mc.getWindow().getHandle());
    }
}
