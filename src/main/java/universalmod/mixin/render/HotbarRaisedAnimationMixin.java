package universalmod.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.JumpingMount;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Animations;

@Mixin(InGameHud.class)
public abstract class HotbarRaisedAnimationMixin {
    @Unique
    private boolean universalmod$hotbarPushed;
    @Unique
    private boolean universalmod$statusBarsPushed;
    @Unique
    private boolean universalmod$experiencePushed;
    @Unique
    private boolean universalmod$mountHealthPushed;
    @Unique
    private boolean universalmod$mountJumpPushed;
    @Unique
    private boolean universalmod$heldItemTooltipPushed;

    @Unique
    private static boolean universalmod$pushRaisedHud(DrawContext context) {
        if (!Animations.active(Animations.RAISED_HOTBAR)) {
            return false;
        }
        Animations animations = Animations.getInstance();
        float progress = animations == null ? 0.0F : animations.hotbarLiftProgress();
        if (progress <= 0.001F) {
            return false;
        }
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, -16.0F * progress, 0.0F);
        return true;
    }

    @Unique
    private static void universalmod$popRaisedHud(DrawContext context, boolean pushed) {
        if (pushed) {
            context.getMatrices().pop();
        }
    }

    @Inject(method = "renderHotbar(FLnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"))
    private void universalmod$beginHotbar(float tickDelta, DrawContext context, CallbackInfo ci) {
        universalmod$hotbarPushed = universalmod$pushRaisedHud(context);
    }

    @Inject(method = "renderHotbar(FLnet/minecraft/client/gui/DrawContext;)V", at = @At("RETURN"))
    private void universalmod$endHotbar(float tickDelta, DrawContext context, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$hotbarPushed);
        universalmod$hotbarPushed = false;
    }

    @Inject(method = "renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"))
    private void universalmod$beginStatusBars(DrawContext context, CallbackInfo ci) {
        universalmod$statusBarsPushed = universalmod$pushRaisedHud(context);
    }

    @Inject(method = "renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("RETURN"))
    private void universalmod$endStatusBars(DrawContext context, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$statusBarsPushed);
        universalmod$statusBarsPushed = false;
    }

    @Inject(method = "renderExperienceBar(Lnet/minecraft/client/gui/DrawContext;I)V", at = @At("HEAD"))
    private void universalmod$beginExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        universalmod$experiencePushed = universalmod$pushRaisedHud(context);
    }

    @Inject(method = "renderExperienceBar(Lnet/minecraft/client/gui/DrawContext;I)V", at = @At("RETURN"))
    private void universalmod$endExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$experiencePushed);
        universalmod$experiencePushed = false;
    }

    @Inject(method = "renderMountHealth(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"))
    private void universalmod$beginMountHealth(DrawContext context, CallbackInfo ci) {
        universalmod$mountHealthPushed = universalmod$pushRaisedHud(context);
    }

    @Inject(method = "renderMountHealth(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("RETURN"))
    private void universalmod$endMountHealth(DrawContext context, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$mountHealthPushed);
        universalmod$mountHealthPushed = false;
    }

    @Inject(method = "renderMountJumpBar(Lnet/minecraft/entity/JumpingMount;Lnet/minecraft/client/gui/DrawContext;I)V", at = @At("HEAD"))
    private void universalmod$beginMountJumpBar(JumpingMount mount, DrawContext context, int x, CallbackInfo ci) {
        universalmod$mountJumpPushed = universalmod$pushRaisedHud(context);
    }

    @Inject(method = "renderMountJumpBar(Lnet/minecraft/entity/JumpingMount;Lnet/minecraft/client/gui/DrawContext;I)V", at = @At("RETURN"))
    private void universalmod$endMountJumpBar(JumpingMount mount, DrawContext context, int x, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$mountJumpPushed);
        universalmod$mountJumpPushed = false;
    }

    @Inject(method = "renderHeldItemTooltip(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"))
    private void universalmod$beginHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        universalmod$heldItemTooltipPushed = universalmod$pushRaisedHud(context);
    }

    @Inject(method = "renderHeldItemTooltip(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("RETURN"))
    private void universalmod$endHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$heldItemTooltipPushed);
        universalmod$heldItemTooltipPushed = false;
    }

    @ModifyArg(method = "renderHotbar(FLnet/minecraft/client/gui/DrawContext;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V", ordinal = 1), index = 1)
    private int universalmod$animateSelectedSlot(int x) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!Animations.active(Animations.HOTBAR_SLOT) || client.player == null) {
            return x;
        }
        int slot = client.player.getInventory().selectedSlot;
        float animated = Animations.getInstance().selectedSlot(slot);
        return Math.round(x - slot * 20.0F + animated * 20.0F);
    }
}
