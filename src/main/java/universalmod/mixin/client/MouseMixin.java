package universalmod.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.Mouse;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.math.Smoother;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.lifecycle.Manager;
import universalmod.event.type.KeyEvent;
import universalmod.event.type.MouseRotationEvent;
import universalmod.module.misc.Zoom;
import universalmod.module.utility.Friends;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private double cursorDeltaX;

    @Shadow
    private double cursorDeltaY;

    @Shadow
    @Final
    private Smoother cursorXSmoother;

    @Shadow
    @Final
    private Smoother cursorYSmoother;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void universalmod$updateMouse(double timeDelta, CallbackInfo ci) {
        if (client.player == null) {
            return;
        }

        double sensitivity = client.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double scaled = sensitivity * sensitivity * sensitivity * 8.0;
        double yawDelta;
        double pitchDelta;

        if (client.options.smoothCameraEnabled) {
            yawDelta = cursorXSmoother.smooth(cursorDeltaX * scaled, timeDelta * scaled);
            pitchDelta = cursorYSmoother.smooth(cursorDeltaY * scaled, timeDelta * scaled);
        } else if (client.options.getPerspective().isFirstPerson() && client.player.isUsingSpyglass()) {
            cursorXSmoother.clear();
            cursorYSmoother.clear();
            yawDelta = cursorDeltaX * sensitivity * sensitivity * sensitivity;
            pitchDelta = cursorDeltaY * sensitivity * sensitivity * sensitivity;
        } else {
            cursorXSmoother.clear();
            cursorYSmoother.clear();
            yawDelta = cursorDeltaX * scaled;
            pitchDelta = cursorDeltaY * scaled;
        }

        int invert = client.options.getInvertYMouse().getValue() ? -1 : 1;
        MouseRotationEvent event = Manager.postEvent(new MouseRotationEvent((float) yawDelta, (float) (pitchDelta * invert)));
        if (!event.isCancelled()) {
            client.getTutorialManager().onUpdateMouse(event.getCursorDeltaX(), event.getCursorDeltaY());
            client.player.changeLookDirection(event.getCursorDeltaX(), event.getCursorDeltaY());
        }

        cursorDeltaX = 0.0;
        cursorDeltaY = 0.0;
        ci.cancel();
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void universalmod$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft.currentScreen != null) {
            return;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                && action == GLFW.GLFW_PRESS
                && Friends.handleMiddleClick(minecraft)) {
            ci.cancel();
            return;
        }

        if (action != GLFW.GLFW_REPEAT) {
            Manager.postEvent(new KeyEvent(minecraft.currentScreen, InputUtil.Type.MOUSE, button, action));
        }
    }

    @Inject(method = "onMouseScroll", at = @At("RETURN"), require = 0)
    private void universalmod$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Zoom zoom = Zoom.getInstance();
        if (zoom != null) {
            zoom.onMouseScroll(vertical);
        }
    }

    @WrapWithCondition(
            method = "onMouseScroll(JDD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerInventory;setSelectedSlot(I)V"
            ),
            require = 0
    )
    private boolean universalmod$blockHotbarScrollWhileZooming(PlayerInventory inventory, int slot) {
        Zoom zoom = Zoom.getInstance();
        return zoom == null || !zoom.isEnabled() || !zoom.isZoomHeld();
    }
}
