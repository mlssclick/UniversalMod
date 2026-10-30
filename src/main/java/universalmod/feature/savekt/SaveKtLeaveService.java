package universalmod.feature.savekt;

import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import universalmod.lifecycle.service.ClientService;

public final class SaveKtLeaveService implements ClientService {
    private static final SaveKtLeaveService INSTANCE = new SaveKtLeaveService();
    private final SaveKtStateMachine state = new SaveKtStateMachine();

    private SaveKtLeaveService() { }

    public static SaveKtLeaveService getInstance() { return INSTANCE; }

    private static void onClientThread(Runnable action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && !client.isOnThread()) {
            client.execute(action);
        } else {
            action.run();
        }
    }

    public void onAttack() {
        onClientThread(() -> state.onAttack(System.currentTimeMillis(), SaveKtManager.getPing()));
    }

    public void onPvpBossbarSeen(UUID id) {
        if (id != null) {
            onClientThread(() -> handleDecision(state.onPvpBossbarSeen(id, System.currentTimeMillis())));
        }
    }

    public void onPvpBossbarRemoved(UUID id) {
        if (id != null) {
            onClientThread(() -> state.onPvpBossbarRemoved(id, System.currentTimeMillis(), SaveKtManager.getPing()));
        }
    }

    public void onBossbarReset() {
        onClientThread(() -> state.onBossbarReset(System.currentTimeMillis(), SaveKtManager.getPing()));
    }

    public void requestLeaveCommand(String command) {
        if (command == null) return;
        onClientThread(() -> handleDecision(state.request(command, System.currentTimeMillis())));
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.getNetworkHandler() == null || client.world == null) {
            state.resetAll();
            return;
        }
        handleDecision(state.tick(System.currentTimeMillis()));
    }

    public boolean isRecentAttack() { return state.isRecentAttack(System.currentTimeMillis()); }
    public boolean isPvp() { return state.isPvp(); }
    public boolean hasPendingCommand() { return state.hasPendingCommand(); }

    public void resetTransient() {
        onClientThread(() -> {
            state.resetTransient();
            SaveKtManager.openScreen = false;
        });
    }

    public void resetAll() {
        onClientThread(() -> {
            state.resetAll();
            SaveKtManager.openScreen = false;
        });
    }

    @Override public void close() { resetAll(); }

    private void handleDecision(SaveKtStateMachine.Decision decision) {
        switch (decision.type()) {
            case NONE -> { }
            case SHOW_CONFIRM -> SaveKtManager.checkLeaveCommand(decision.command());
            case SEND -> SaveKtManager.sendFinalCommand(decision.command());
        }
    }
}
