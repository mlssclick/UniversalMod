package universalmod.mixin.render;

import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import universalmod.module.utility.Friends;
import universalmod.feature.friend.FriendRepository;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererFriendMixin {
    private static final int UNIVERSALMOD_FRIEND_ARMOR_GREEN = 0x33F233;

    @Redirect(method = "renderArmor", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getEquippedStack(Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack universalmod$friendArmor(LivingEntity entity, EquipmentSlot slot) {
        ItemStack original = entity.getEquippedStack(slot);
        Friends friends = Friends.getInstance();
        if (original.isEmpty() || friends == null || !friends.isEnabled() || !(entity instanceof PlayerEntity player) || !FriendRepository.isFriend(player)) {
            return original;
        }
        ItemStack replacement = switch (slot) {
            case HEAD -> new ItemStack(Items.LEATHER_HELMET);
            case CHEST -> new ItemStack(Items.LEATHER_CHESTPLATE);
            case LEGS -> new ItemStack(Items.LEATHER_LEGGINGS);
            case FEET -> new ItemStack(Items.LEATHER_BOOTS);
            default -> ItemStack.EMPTY;
        };
        if (replacement.isEmpty()) return original;
        if (replacement.getItem() instanceof DyeableItem dyeable) {
            dyeable.setColor(replacement, UNIVERSALMOD_FRIEND_ARMOR_GREEN);
        }
        return replacement;
    }
}
