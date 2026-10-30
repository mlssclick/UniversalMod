package universalmod.module.misc.coinprice;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;

public final class CoinPrice extends Module {
    private static CoinPrice instance;
    private static boolean callbackRegistered;

    public CoinPrice() {
        super("coin_price", "CoinPrice", ModuleCategory.MISC);
        instance = this;
        if (!callbackRegistered) {
            callbackRegistered = true;
            ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> CoinPriceTooltipHandler.appendCoinPrice(lines));
        }
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    @Override
    protected void onEnable() {
        CoinRateService.startPolling();
        CoinRateService.refreshNow();
    }
}
