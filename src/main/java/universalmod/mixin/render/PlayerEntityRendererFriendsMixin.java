package universalmod.mixin.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.module.render.InvisibleTags;
import universalmod.module.utility.Friends;
import universalmod.feature.friend.FriendRepository;





@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererFriendsMixin {
    @Unique
    private static final int UNIVERSALMOD$FRIEND_ARMOR_GREEN = 0x33F233;

    @Unique
    private static final ItemStack UNIVERSALMOD$FRIEND_HELMET = universalmod$dyedLeather(Items.LEATHER_HELMET);

    @Unique
    private static final ItemStack UNIVERSALMOD$FRIEND_CHESTPLATE = universalmod$dyedLeather(Items.LEATHER_CHESTPLATE);

    @Unique
    private static final ItemStack UNIVERSALMOD$FRIEND_LEGGINGS = universalmod$dyedLeather(Items.LEATHER_LEGGINGS);

    @Unique
    private static final ItemStack UNIVERSALMOD$FRIEND_BOOTS = universalmod$dyedLeather(Items.LEATHER_BOOTS);

    @Inject(
            method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"),
            require = 0
    )
    private void universalmod$replaceFriendArmor(
            AbstractClientPlayerEntity player,
            PlayerEntityRenderState state,
            float tickDelta,
            CallbackInfo ci
    ) {
        InvisibleTags.applyVanillaInvisibleTag(player, state, tickDelta);

        Friends friends = Friends.getInstance();
        if (friends == null
                || !friends.isEnabled()
                || !FriendRepository.isFriend(player.getName().getString())) {
            return;
        }

        if (!state.equippedHeadStack.isEmpty()) {
            state.equippedHeadStack = UNIVERSALMOD$FRIEND_HELMET;
        }
        if (!state.equippedChestStack.isEmpty()) {
            state.equippedChestStack = UNIVERSALMOD$FRIEND_CHESTPLATE;
        }
        if (!state.equippedLegsStack.isEmpty()) {
            state.equippedLegsStack = UNIVERSALMOD$FRIEND_LEGGINGS;
        }
        if (!state.equippedFeetStack.isEmpty()) {
            state.equippedFeetStack = UNIVERSALMOD$FRIEND_BOOTS;
        }
    }

    @Unique
    private static ItemStack universalmod$dyedLeather(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(
                DataComponentTypes.DYED_COLOR,
                new DyedColorComponent(UNIVERSALMOD$FRIEND_ARMOR_GREEN, true)
        );
        return stack;
    }
}
