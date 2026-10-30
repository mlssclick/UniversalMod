package universalmod.feature.savekt;

import net.minecraft.client.Minecraft;
import universalmod.integration.minecraft.ClientPlayNetworkHandlerHelper;
import universalmod.lifecycle.service.ClientService;

import java.util.UUID;

public final class SaveKtLeaveService implements ClientService {
    private static final SaveKtLeaveService INSTANCE = new SaveKtLeaveService();

    private final SaveKtStateMachine state = new SaveKtStateMachine();

    private SaveKtLeaveService() {
    }

    public static SaveKtLeaveService getInstance() {
        return INSTANCE;
    }

    public void onAttack() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) {
            client.execute(this::onAttack);
            return;
        }
        state.onAttack(System.currentTimeMillis(), SaveKtManager.getPing());
    }

    public void onPvpBossbarSeen(UUID id) {
        if (id == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) {
            client.execute(() -> onPvpBossbarSeen(id));
            return;
        }
        handleDecision(state.onPvpBossbarSeen(id, System.currentTimeMillis()));
    }

    public void onPvpBossbarRemoved(UUID id) {
        if (id == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) {
            client.execute(() -> onPvpBossbarRemoved(id));
            return;
        }
        state.onPvpBossbarRemoved(id, System.currentTimeMillis(), SaveKtManager.getPing());
    }

    public void onBossbarReset() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) {
            client.execute(this::onBossbarReset);
            return;
        }
        state.onBossbarReset(System.currentTimeMillis(), SaveKtManager.getPing());
    }

    public void requestLeaveCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) {
            client.execute(() -> requestLeaveCommand(command));
            return;
        }
        handleDecision(state.request(command, System.currentTimeMillis()));
    }

    public void tick() {
        handleDecision(state.tick(System.currentTimeMillis()));
    }

    public boolean isRecentAttack() {
        return state.isRecentAttack(System.currentTimeMillis());
    }

    public boolean isPvp() {
        return state.isPvp();
    }

    public boolean hasPendingCommand() {
        return state.hasPendingCommand();
    }

    public void resetTransient() {
        state.resetTransient();
        SaveKtManager.openScreen = false;
    }

    public void resetAll() {
        state.resetAll();
        SaveKtManager.openScreen = false;
    }

    @Override
    public void close() {
        resetAll();
    }

    private void handleDecision(SaveKtStateMachine.Decision decision) {
        if (decision == null || decision.type() == SaveKtStateMachine.DecisionType.NONE) {
            return;
        }
        String command = decision.command();
        if (decision.type() == SaveKtStateMachine.DecisionType.SHOW_CONFIRM) {
            SaveKtManager.resetTransientState();
            SaveKtManager.checkLeaveCommand(command);
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (client.getConnection() instanceof ClientPlayNetworkHandlerHelper helper) {
            helper.sendFinalCommand(command);
        } else {
        }
    }

}
