package universalmod.feature;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import universalmod.storage.RepositoryStorage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FriendHelper {
    private static final List<String> FRIENDS = new ArrayList<>();

    private FriendHelper() {
    }

    public static void load() {
        setFriends(RepositoryStorage.loadStringList("friends.universalmod", "friends"));
    }

    public static void save() {
        RepositoryStorage.saveStringList("friends.universalmod", "friends", getFriendNames());
    }

    public static void addFriend(PlayerEntity player) {
        if (player != null) {
            addFriend(player.getName().getString());
        }
    }

    public static boolean isFriend(Entity entity) {
        return entity instanceof PlayerEntity player && isFriend(player.getName().getString());
    }

    public static boolean isFriend(String name) {
        String normalized = normalizeName(name);
        return !normalized.isEmpty() && FRIENDS.stream().anyMatch(friend -> friend.equalsIgnoreCase(normalized));
    }

    public static void addFriend(String name) {
        String normalized = normalizeName(name);
        if (!normalized.isEmpty() && !isFriend(normalized)) {
            FRIENDS.add(normalized);
        }
    }

    public static void addFriendAndSave(String name) {
        addFriend(name);
        save();
    }

    public static void removeFriend(PlayerEntity player) {
        if (player != null) {
            removeFriend(player.getName().getString());
        }
    }

    public static void removeFriend(String name) {
        String normalized = normalizeName(name);
        FRIENDS.removeIf(friend -> friend.equalsIgnoreCase(normalized));
    }

    public static void removeFriendAndSave(String name) {
        removeFriend(name);
        save();
    }

    public static void clear() {
        FRIENDS.clear();
    }

    public static void clearAndSave() {
        clear();
        save();
    }

    public static List<String> getFriendNames() {
        return new ArrayList<>(FRIENDS);
    }

    public static int size() {
        return FRIENDS.size();
    }

    public static void setFriends(List<String> names) {
        FRIENDS.clear();
        Set<String> unique = new LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                String normalized = normalizeName(name);
                if (!normalized.isEmpty()) {
                    unique.add(normalized);
                }
            }
        }
        FRIENDS.addAll(unique);
    }

    public static Set<String> friends() {
        return new LinkedHashSet<>(FRIENDS);
    }

    public static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\p{Cf}", "").trim();
        return cleaned.replaceAll("\\s+", "");
    }
}
