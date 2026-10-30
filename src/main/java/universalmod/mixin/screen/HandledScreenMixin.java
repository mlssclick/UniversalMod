package universalmod.mixin.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.lifecycle.Manager;
import universalmod.event.type.HandledScreenEvent;
import universalmod.module.render.Animations;
import universalmod.module.misc.ServerHelper.ItemInfo;
import universalmod.module.utility.ItemScroller;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow
    @Nullable
    protected Slot focusedSlot;

    @Shadow
    protected int backgroundWidth;

    @Shadow
    protected int backgroundHeight;

    @Unique
    private boolean universalmod$itemAnimationMatricesPushed;

    @Inject(method = "render", at = @At("HEAD"))
    private void universalmod$handledScreenRender(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        Manager.postEvent(new HandledScreenEvent(context, focusedSlot, backgroundWidth, backgroundHeight));
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), require = 0)
    private void universalmod$itemScrollerScroll(double mouseX, double mouseY, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        if (vertical != 0.0D) ItemScroller.handleScroll(focusedSlot);
    }

    @Inject(method = "drawSlot", at = @At("HEAD"), require = 0)
    private void universalmod$beginItemAnimation(DrawContext context, Slot slot, CallbackInfo ci) {
        universalmod$itemAnimationMatricesPushed = false;
        if (!Animations.active(Animations.ITEMS) || context == null || slot == null) {
            return;
        }

        boolean focused = slot == focusedSlot && !slot.getStack().isEmpty();
        float scale = Animations.getInstance().itemScale(slot, focused);
        if (Math.abs(scale - 1.0F) <= 0.0001F) {
            return;
        }

        context.getMatrices().push();
        context.getMatrices().translate(slot.x + 8.0F, slot.y + 8.0F, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.getMatrices().translate(-(slot.x + 8.0F), -(slot.y + 8.0F), 0.0F);
        universalmod$itemAnimationMatricesPushed = true;
    }

    @Inject(method = "drawSlot", at = @At("RETURN"), require = 0)
    private void universalmod$endItemAnimation(DrawContext context, Slot slot, CallbackInfo ci) {
        if (universalmod$itemAnimationMatricesPushed) {
            context.getMatrices().pop();
            universalmod$itemAnimationMatricesPushed = false;
        }
    }

    @Inject(
            method = "drawSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/item/ItemStack;III)V"
            ),
            require = 0
    )
    private void universalmod$serverHelperSlotFill(DrawContext context, Slot slot, CallbackInfo ci) {
        if (context == null || slot == null) {
            return;
        }

        ItemStack stack = slot.getStack();
        int colorRgb = ItemInfo.getSlotFillColor(stack);
        if (colorRgb == -1) {
            return;
        }

        double pulse = (Math.sin(System.currentTimeMillis() / 200.0D) + 1.0D) * 0.5D;
        int alpha = (int) (20.0D + 70.0D * pulse);
        context.fill(
                slot.x,
                slot.y,
                slot.x + 16,
                slot.y + 16,
                (alpha << 24) | colorRgb
        );
    }
}
