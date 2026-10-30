package universalmod.module.misc.coinprice;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class CoinPriceTooltipHandler {
    private CoinPriceTooltipHandler() {
    }

    public static void appendCoinPrice(List<Component> lines) {
        if (!CoinPrice.isActive() || lines == null || lines.isEmpty()) {
            return;
        }

        double averageRate = CoinRateService.getAverageRate();
        if (averageRate <= 0.0D || Double.isNaN(averageRate) || Double.isInfinite(averageRate)) {
            return;
        }

        for (int index = 0; index < lines.size(); index++) {
            Component original = lines.get(index);
            if (original == null) {
                continue;
            }
            String rawText = original.getString();
            if (PriceLineParser.hasCoinSuffix(rawText) || !PriceLineParser.isSupportedPriceLine(rawText)) {
                continue;
            }

            Double price = PriceLineParser.extractPrice(rawText);
            if (price == null || price <= 0.0D) {
                continue;
            }

            double coinPrice = price / averageRate;
            if (coinPrice <= 0.0D || Double.isNaN(coinPrice) || Double.isInfinite(coinPrice)) {
                continue;
            }

            String formattedCoins = String.format(Locale.US, "%.2f", coinPrice);
            MutableComponent appended = original.copy()
                    .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(formattedCoins + " ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("|").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("❘").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(Component.literal("|").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(")").withStyle(ChatFormatting.GRAY));
            lines.set(index, appended);
        }
    }
}
