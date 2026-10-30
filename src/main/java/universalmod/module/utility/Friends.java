package universalmod.module.utility;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.service.language.LanguageManager;
import universalmod.feature.FriendHelper;
import universalmod.service.ChatMessage;

public final class Friends extends Module {
    private static Friends instance;

    public Friends() {
        super("friends", "Friends", ModuleCategory.UTILS);
        instance = this;
        setEnabled(true);
    }

    public static boolean handleMiddleClick(Minecraft minecraft) {
        Friends module = instance;
        if (module == null || !module.isEnabled() || minecraft == null || minecraft.player == null) {
            return false;
        }
        if (!(minecraft.hitResult instanceof EntityHitResult entityHitResult)) {
            return false;
        }
        if (!(entityHitResult.getEntity() instanceof Player player)) {
            return false;
        }
        if (player == minecraft.player) {
            return false;
        }

        String name = FriendHelper.normalizeName(player.getName().getString());
        if (name.isEmpty()) {
            return false;
        }

        boolean wasFriend = FriendHelper.isFriend(name);
        if (wasFriend) {
            FriendHelper.removeFriendAndSave(name);
            ChatMessage.brandmessage(Component.literal(LanguageManager.translateFormat("friends.removed", name)).withStyle(ChatFormatting.GREEN));
        } else {
            FriendHelper.addFriendAndSave(name);
            ChatMessage.brandmessage(Component.literal(LanguageManager.translateFormat("friends.added", name)).withStyle(ChatFormatting.GREEN));
        }
        return true;
    }

    public static Friends getInstance() {
        return instance;
    }
}
