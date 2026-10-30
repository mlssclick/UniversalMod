package universalmod.mixin.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.event.type.HandledScreenEvent;
import universalmod.module.render.Animations;
import universalmod.module.utility.ItemScroller;
import universalmod.lifecycle.ClientRuntime;
import universalmod.module.misc.serverhelper.ServerItemMetadataService;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow
    protected Slot focusedSlot;
    @Unique
    private boolean universalmod$itemAnimationPushed;

    @Inject(method = "render", at = @At("TAIL"))
    private void universalmod$itemScroller(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ClientRuntime.postEvent(new HandledScreenEvent(context, focusedSlot, 0, 0));
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), require = 0)
    private void universalmod$itemScrollerScroll(double mouseX, double mouseY, double amount, CallbackInfoReturnable<Boolean> cir) {
        if (amount != 0.0D) ItemScroller.handleScroll(focusedSlot);
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void universalmod$slotHead(DrawContext context, Slot slot, CallbackInfo ci) {
        universalmod$itemAnimationPushed = false;
        if (Animations.active(Animations.ITEMS) && slot != null) {
            Animations a = Animations.getInstance();
            float scale = a == null ? 1f : a.itemScale(slot, slot == focusedSlot);
            if (Math.abs(scale - 1f) > .001f) {
                context.getMatrices().push();
                universalmod$itemAnimationPushed = true;
                context.getMatrices().translate(slot.x + 8, slot.y + 8, 0);
                context.getMatrices().scale(scale, scale, 1);
                context.getMatrices().translate(-(slot.x + 8), -(slot.y + 8), 0);
            }
        }
        if (context == null || slot == null) return;
        ItemStack stack = slot.getStack();
        int rgb = ServerItemMetadataService.getSlotFillColor(stack);
        if (rgb == -1) return;
        double pulse = (Math.sin(System.currentTimeMillis() / 200.0D) + 1.0D) * .5D;
        int alpha = (int) (20.0D + 70.0D * pulse);
        context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, (alpha << 24) | rgb);
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void universalmod$slotTail(DrawContext context, Slot slot, CallbackInfo ci) {
        if (universalmod$itemAnimationPushed) {
            context.getMatrices().pop();
            universalmod$itemAnimationPushed = false;
        }
    }
}
