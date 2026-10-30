package universalmod.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.misc.Zoom;
import universalmod.module.render.Hud;
import universalmod.module.utility.Friends;
import universalmod.event.type.MouseRotationEvent;
import universalmod.lifecycle.ClientRuntime;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Zoom zoom = Zoom.getInstance();
        if (zoom == null || !zoom.isEnabled() || !zoom.isZoomHeld()) return;
        zoom.onMouseScroll(vertical);
        ci.cancel();
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void universalmod$friends(long window, int button, int action, int mods, CallbackInfo ci) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && action == GLFW.GLFW_PRESS)
            Friends.handleMiddleClick(MinecraftClient.getInstance());
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void universalmod$chatEditorRelease(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action != GLFW.GLFW_RELEASE || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof ChatScreen)) return;
        Hud hud = Hud.getInstance();
        if (hud != null)
            hud.handleMouseReleased(universalmod$scaledX(client, client.mouse.getX()), universalmod$scaledY(client, client.mouse.getY()), button);
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void universalmod$chatEditorDrag(long window, double mouseX, double mouseY, CallbackInfo ci) {
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof ChatScreen)) return;
        double x = universalmod$scaledX(client, mouseX), y = universalmod$scaledY(client, mouseY);
        Hud hud = Hud.getInstance();
        if (hud != null) hud.handleMouseDragged(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void universalmod$chatEditorScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof ChatScreen)) return;
        double x = universalmod$scaledX(client, client.mouse.getX()), y = universalmod$scaledY(client, client.mouse.getY());
        Hud hud = Hud.getInstance();
        if (hud != null && hud.handleMouseScrolled(x, y, vertical)) ci.cancel();
    }

    @Unique
    private static double universalmod$scaledX(MinecraftClient client, double mouseX) {
        return mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
    }

    @Unique
    private static double universalmod$scaledY(MinecraftClient client, double mouseY) {
        return mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
    }

    @Redirect(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void universalmod$freeLookClientPlayer(ClientPlayerEntity player, double deltaX, double deltaY) {
        MouseRotationEvent event = ClientRuntime.postEvent(new MouseRotationEvent((float) deltaX, (float) deltaY));
        if (event.isCancelled()) return;
        double x = event.getCursorDeltaX(), y = event.getCursorDeltaY();
        player.changeLookDirection(x, y);
    }
}
