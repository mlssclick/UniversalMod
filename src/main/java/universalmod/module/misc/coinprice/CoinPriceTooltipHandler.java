package universalmod.module.misc.coinprice;

import java.util.List;
import java.util.Locale;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;

public final class CoinPriceTooltipHandler {
    private CoinPriceTooltipHandler() {
    }

    public static void appendCoinPrice(List<Text> lines) {
        if (!CoinPrice.isActive() || lines == null || lines.isEmpty()) {
            return;
        }

        double averageRate = CoinRateService.getAverageRate();
        if (averageRate <= 0.0D || Double.isNaN(averageRate) || Double.isInfinite(averageRate)) {
            return;
        }

        for (int index = 0; index < lines.size(); index++) {
            Text original = lines.get(index);
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
            MutableText appended = original.copy()
                    .append(Text.literal(" (").formatted(Formatting.GRAY))
                    .append(Text.literal(formattedCoins + " ").formatted(Formatting.YELLOW))
                    .append(Text.literal("|").formatted(Formatting.GOLD))
                    .append(Text.literal("❘").formatted(Formatting.YELLOW, Formatting.BOLD))
                    .append(Text.literal("|").formatted(Formatting.GOLD))
                    .append(Text.literal(")").formatted(Formatting.GRAY));
            lines.set(index, appended);
        }
    }
}
