package universalmod.feature.friend;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import universalmod.storage.RepositoryStorage;

import java.util.*;
import java.util.stream.Collectors;

public final class FriendRepository {
    private static final List<Friend> FRIENDS = new ArrayList<>();

    private FriendRepository() {
    }

    public static void load() {
        setFriends(RepositoryStorage.loadStringList("friends.universalmod", "friends"));
    }

    public static void save() {
        RepositoryStorage.saveStringList("friends.universalmod", "friends", getFriendNames());
    }

    public static void addFriend(PlayerEntity p) {
        if (p != null) addFriend(p.getName().getString());
    }

    public static boolean isFriend(Entity e) {
        return e instanceof PlayerEntity p && isFriend(p.getName().getString());
    }

    public static boolean isFriend(String n) {
        String s = normalizeName(n);
        return !s.isEmpty() && FRIENDS.stream().anyMatch(f -> f.getName().equalsIgnoreCase(s));
    }

    public static void addFriend(String n) {
        String s = normalizeName(n);
        if (!s.isEmpty() && !isFriend(s)) FRIENDS.add(new Friend(s));
    }

    public static void addFriendAndSave(String n) {
        addFriend(n);
        save();
    }

    public static void removeFriend(PlayerEntity p) {
        if (p != null) removeFriend(p.getName().getString());
    }

    public static void removeFriend(String n) {
        String s = normalizeName(n);
        FRIENDS.removeIf(f -> f.getName().equalsIgnoreCase(s));
    }

    public static void removeFriendAndSave(String n) {
        removeFriend(n);
        save();
    }

    public static void clear() {
        FRIENDS.clear();
    }

    public static void clearAndSave() {
        clear();
        save();
    }

    public static List<Friend> getFriends() {
        return FRIENDS;
    }

    public static List<String> getFriendNames() {
        return FRIENDS.stream().map(Friend::getName).collect(Collectors.toList());
    }

    public static int size() {
        return FRIENDS.size();
    }

    public static void setFriends(List<String> names) {
        FRIENDS.clear();
        Set<String> unique = new LinkedHashSet<>();
        if (names != null) for (String n : names) {
            String s = normalizeName(n);
            if (!s.isEmpty()) unique.add(s);
        }
        for (String n : unique) FRIENDS.add(new Friend(n));
    }

    public static Set<String> friends() {
        return new LinkedHashSet<>(getFriendNames());
    }

    public static String normalizeName(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("\\p{Cf}", "").trim();
        return cleaned.replaceAll("\\s+", "");
    }
}
