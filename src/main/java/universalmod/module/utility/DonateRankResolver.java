package universalmod.module.utility;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DonateRankResolver {
    static final int NONE = 0;
    static final int CUSTOM = 900;
    private static final int GRIEFER = 100, MUSTANG = 200, GHAST = 300, WITHER = 400, KRAKEN = 500, DRAGON = 600, STINGER = 700, ETERNITY = 800;
    private static final int STAZHER = 1000, JUNIOR_STAFF = 1100, STAFF = 1200, JUNIOR_SPECTATOR = 1300, LEAD_STAFF = 1400, SPECTATOR = 1500, SENIOR_STAFF = 1600;
    private DonateRankResolver() {}

    static int resolveVisible(PlayerInfo info, Player player) {
        for (String sample : samples(info, player)) {
            int rank = resolveKnown(normalize(sample));
            if (rank != NONE) return rank;
        }
        return NONE;
    }

    private static List<String> samples(PlayerInfo info, Player player) {
        List<String> samples = new ArrayList<>();
        if (info.getTabListDisplayName() != null) samples.add(info.getTabListDisplayName().getString());
        PlayerTeam team = info.getTeam();
        if (team != null) {
            if (team.getPlayerPrefix() != null) samples.add(team.getPlayerPrefix().getString());
            if (team.getDisplayName() != null) samples.add(team.getDisplayName().getString());
            samples.add(team.getName());
        }
        if (player != null) {
            samples.add(player.getDisplayName().getString());
            samples.add(player.getName().getString());
            samples.add(player.getScoreboardName());
        }
        return samples;
    }

    static String normalize(String text) {
        String plain = LegacyTextFormatter.strip(clean(text)).toLowerCase(Locale.ROOT).replace('ё', 'е');
        StringBuilder normalized = new StringBuilder(plain.length());
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (!Character.isWhitespace(c)) normalized.append(c);
        }
        return normalized.toString();
    }

    static int resolveKnown(String value) {
        if (value == null || value.isBlank()) return NONE;
        if (value.contains("ст.сотрудник")) return SENIOR_STAFF;
        if (value.contains("вед.сотрудник")) return LEAD_STAFF;
        if (value.contains("мл.спектатор")) return JUNIOR_SPECTATOR;
        if (value.contains("мл.сотрудник")) return JUNIOR_STAFF;
        if (value.contains("спектатор")) return SPECTATOR;
        if (value.contains("сотрудник")) return STAFF;
        if (value.contains("стажер")) return STAZHER;
        if (value.contains("eternity")) return ETERNITY;
        if (value.contains("stinger")) return STINGER;
        if (value.contains("dragon")) return DRAGON;
        if (value.contains("kraken")) return KRAKEN;
        if (value.contains("wither")) return WITHER;
        if (value.contains("ghast")) return GHAST;
        if (value.contains("mustang")) return MUSTANG;
        if (value.contains("griefer")) return GRIEFER;
        return NONE;
    }

    private static String clean(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder builder = new StringBuilder(text.length());
        boolean skip = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skip) { skip = false; continue; }
            if (c == '§') { skip = true; continue; }
            if (!Character.isISOControl(c)) builder.append(c);
        }
        return builder.toString().trim();
    }
}
