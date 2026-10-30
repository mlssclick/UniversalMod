package universalmod.mixin.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.JumpingMount;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.Animations;

@Mixin(InGameHud.class)
public abstract class InGameHudAnimationsMixin {
    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    @Final
    private PlayerListHud playerListHud;

    @Unique
    private boolean universalmod$hotbarMatricesPushed;
    @Unique
    private boolean universalmod$statusBarsMatricesPushed;
    @Unique
    private boolean universalmod$experienceBarMatricesPushed;
    @Unique
    private boolean universalmod$experienceLevelMatricesPushed;
    @Unique
    private boolean universalmod$mountHealthMatricesPushed;
    @Unique
    private boolean universalmod$mountJumpMatricesPushed;
    @Unique
    private boolean universalmod$heldItemTooltipMatricesPushed;

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

    @Inject(
            method = "renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD")
    )
    private void universalmod$beginRaisedHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        universalmod$hotbarMatricesPushed = universalmod$pushRaisedHud(context);
    }

    @Inject(
            method = "renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("RETURN")
    )
    private void universalmod$endRaisedHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$hotbarMatricesPushed);
        universalmod$hotbarMatricesPushed = false;
    }

    @Inject(method = "renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"))
    private void universalmod$beginStatusBars(DrawContext context, CallbackInfo ci) {
        universalmod$statusBarsMatricesPushed = universalmod$pushRaisedHud(context);
    }

    @Inject(method = "renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("RETURN"))
    private void universalmod$endStatusBars(DrawContext context, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$statusBarsMatricesPushed);
        universalmod$statusBarsMatricesPushed = false;
    }

    @Inject(method = "renderExperienceBar(Lnet/minecraft/client/gui/DrawContext;I)V", at = @At("HEAD"))
    private void universalmod$beginExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        universalmod$experienceBarMatricesPushed = universalmod$pushRaisedHud(context);
    }

    @Inject(method = "renderExperienceBar(Lnet/minecraft/client/gui/DrawContext;I)V", at = @At("RETURN"))
    private void universalmod$endExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$experienceBarMatricesPushed);
        universalmod$experienceBarMatricesPushed = false;
    }

    @Inject(
            method = "renderExperienceLevel(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD")
    )
    private void universalmod$beginExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        universalmod$experienceLevelMatricesPushed = universalmod$pushRaisedHud(context);
    }

    @Inject(
            method = "renderExperienceLevel(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("RETURN")
    )
    private void universalmod$endExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$experienceLevelMatricesPushed);
        universalmod$experienceLevelMatricesPushed = false;
    }

    @Inject(method = "renderMountHealth(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"))
    private void universalmod$beginMountHealth(DrawContext context, CallbackInfo ci) {
        universalmod$mountHealthMatricesPushed = universalmod$pushRaisedHud(context);
    }

    @Inject(method = "renderMountHealth(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("RETURN"))
    private void universalmod$endMountHealth(DrawContext context, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$mountHealthMatricesPushed);
        universalmod$mountHealthMatricesPushed = false;
    }

    @Inject(
            method = "renderMountJumpBar(Lnet/minecraft/entity/JumpingMount;Lnet/minecraft/client/gui/DrawContext;I)V",
            at = @At("HEAD")
    )
    private void universalmod$beginMountJumpBar(JumpingMount mount, DrawContext context, int x, CallbackInfo ci) {
        universalmod$mountJumpMatricesPushed = universalmod$pushRaisedHud(context);
    }

    @Inject(
            method = "renderMountJumpBar(Lnet/minecraft/entity/JumpingMount;Lnet/minecraft/client/gui/DrawContext;I)V",
            at = @At("RETURN")
    )
    private void universalmod$endMountJumpBar(JumpingMount mount, DrawContext context, int x, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$mountJumpMatricesPushed);
        universalmod$mountJumpMatricesPushed = false;
    }

    @Inject(method = "renderHeldItemTooltip(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"))
    private void universalmod$beginHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        universalmod$heldItemTooltipMatricesPushed = universalmod$pushRaisedHud(context);
    }

    @Inject(method = "renderHeldItemTooltip(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("RETURN"))
    private void universalmod$endHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        universalmod$popRaisedHud(context, universalmod$heldItemTooltipMatricesPushed);
        universalmod$heldItemTooltipMatricesPushed = false;
    }

    @ModifyArg(
            method = "renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1
            ),
            index = 2,
            require = 0
    )
    private int universalmod$animateSelectedHotbarSlot(int x) {
        if (!Animations.active(Animations.HOTBAR_SLOT) || client == null || client.player == null) {
            return x;
        }
        int slot = client.player.getInventory().selectedSlot;
        float animated = Animations.getInstance().selectedSlot(slot);
        return Math.round(x - slot * 20.0F + animated * 20.0F);
    }

    @Inject(method = "renderPlayerList", at = @At("TAIL"), require = 0)
    private void universalmod$renderClosingPlayerList(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!Animations.active(Animations.TAB)
                || client == null
                || client.world == null
                || client.options.playerListKey.isPressed()
                || Animations.getInstance().tabProgress() <= 0.001F) {
            return;
        }

        Scoreboard scoreboard = client.world.getScoreboard();
        playerListHud.render(
                context,
                context.getScaledWindowWidth(),
                scoreboard,
                scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST)
        );
    }
}
