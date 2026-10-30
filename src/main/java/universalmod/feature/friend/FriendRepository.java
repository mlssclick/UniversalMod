package universalmod.feature.friend;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.PlayerEntity;
import universalmod.util.QuickLogger;
import universalmod.storage.RepositoryStorage;






public final class FriendRepository implements QuickLogger {
    private static final String STORAGE_FILE = "friends.universalmod";
    private static final String STORAGE_KEY = "friends";
    private static final List<Friend> friends = new ArrayList<>();

    private FriendRepository() {
    }

    public static void addFriend(String name) {
        String normalized = normalizeName(name);
        if (!normalized.isEmpty() && !isFriend(normalized)) {
            friends.add(new Friend(normalized));
        }
    }

    public static void removeFriend(String name) {
        String normalized = normalizeName(name);
        friends.removeIf(friend -> friend.name().equalsIgnoreCase(normalized));
    }

    public static boolean shouldAttack(PlayerEntity player) {
        return player == null || !isFriend(player.getNameForScoreboard());
    }

    public static boolean isFriend(String friend) {
        String normalized = normalizeName(friend);
        return !normalized.isEmpty() && friends.stream().anyMatch(f -> f.name().equalsIgnoreCase(normalized));
    }

    public static Friend getFriend(String name) {
        String normalized = normalizeName(name);
        return friends.stream().filter(f -> f.name().equalsIgnoreCase(normalized)).findFirst().orElse(null);
    }

    public static void clear() {
        friends.clear();
    }

    public static void save() {
        RepositoryStorage.saveStringList(STORAGE_FILE, STORAGE_KEY,
                friends.stream().map(Friend::name).toList());
    }

    public static void load() {
        setFriends(RepositoryStorage.loadStringList(STORAGE_FILE, STORAGE_KEY));
    }

    public static List<Friend> getFriends() {
        return friends;
    }

    public static void setFriends(List<String> names) {
        friends.clear();
        Set<String> unique = new LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                String normalized = normalizeName(name);
                if (!normalized.isEmpty()) unique.add(normalized);
            }
        }
        for (String name : unique) friends.add(new Friend(name));
    }

    public static String normalizeName(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("\\p{Cf}", "").trim();
        return cleaned.replaceAll("\\s+", "");
    }
}
