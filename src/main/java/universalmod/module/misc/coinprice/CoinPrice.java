package universalmod.module.misc.coinprice;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import universalmod.module.ModuleInformation;

@ModuleInformation(moduleName = "CoinPrice", moduleCategory = ModuleCategory.MISC)
public final class CoinPrice extends Module {
    private static CoinPrice instance;
    private static boolean callbackRegistered;

    public CoinPrice() {
        instance = this;
        if (!callbackRegistered) {
            callbackRegistered = true;
            ItemTooltipCallback.EVENT.register((stack, context, lines) -> CoinPriceTooltipHandler.appendCoinPrice(lines));
        }
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    @Override
    public void onEnable() {
        CoinRateService.startPolling();
        CoinRateService.refreshNow();
    }
}
