package universalmod.module.utility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;
import universalmod.feature.friend.FriendRepository;
import universalmod.service.language.LanguageManager;
import universalmod.service.chat.ChatMessenger;

@ModuleInformation(
        moduleName = "Friends",
        moduleCategory = ModuleCategory.UTILS
)
public final class Friends extends Module {
    private static Friends instance;

    public Friends() {
        instance = this;
        setEnabled(true);
    }

    public static Friends getInstance() {
        return instance;
    }

    public static boolean handleMiddleClick(MinecraftClient client) {
        Friends module = instance;
        if (module == null || !module.isEnabled() || client == null || client.player == null) {
            return false;
        }

        if (!(client.crosshairTarget instanceof EntityHitResult entityHitResult)) {
            return false;
        }

        if (!(entityHitResult.getEntity() instanceof PlayerEntity player) || player == client.player) {
            return false;
        }

        String name = FriendRepository.normalizeName(player.getName().getString());
        if (name.isEmpty()) {
            return false;
        }

        boolean wasFriend = FriendRepository.isFriend(name);
        if (wasFriend) {
            FriendRepository.removeFriend(name);
            FriendRepository.save();
            ChatMessenger.sendArrow(
                    Text.literal(LanguageManager.translateFormat("friends.removed", name)).formatted(Formatting.GREEN)
            );
        } else {
            FriendRepository.addFriend(name);
            FriendRepository.save();
            ChatMessenger.sendArrow(
                    Text.literal(LanguageManager.translateFormat("friends.added", name)).formatted(Formatting.GREEN)
            );
        }

        return true;
    }

}
