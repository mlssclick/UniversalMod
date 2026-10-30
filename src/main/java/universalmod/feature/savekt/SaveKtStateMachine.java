package universalmod.feature.savekt;

import java.util.Objects;
import java.util.UUID;
public final class SaveKtStateMachine {
    private long attackUntilMillis;
    private long pvpClearAtMillis;
    private boolean pvp;
    private UUID pvpBossbarId;
    private String pendingCommand;

    public void onAttack(long nowMillis, long protectionMillis) {
        attackUntilMillis = Math.max(attackUntilMillis, safeAdd(nowMillis, Math.max(0L, protectionMillis)));
    }

    public Decision onPvpBossbarSeen(UUID id, long nowMillis) {
        pvpBossbarId = Objects.requireNonNull(id, "id");
        pvp = true;
        pvpClearAtMillis = 0L;
        return decide(nowMillis);
    }

    public void onPvpBossbarRemoved(UUID id, long nowMillis, long clearDelayMillis) {
        if (id != null && id.equals(pvpBossbarId)) {
            schedulePvpClear(nowMillis, clearDelayMillis);
        }
    }

    public void onBossbarReset(long nowMillis, long clearDelayMillis) {
        schedulePvpClear(nowMillis, clearDelayMillis);
    }

    public Decision request(String command, long nowMillis) {
        pendingCommand = command;
        return decide(nowMillis);
    }

    public Decision tick(long nowMillis) {
        if (attackUntilMillis > 0L && nowMillis >= attackUntilMillis) {
            attackUntilMillis = 0L;
        }
        if (pvpClearAtMillis > 0L && nowMillis >= pvpClearAtMillis) {
            pvpClearAtMillis = 0L;
            pvp = false;
            pvpBossbarId = null;
        }
        return decide(nowMillis);
    }

    public boolean isRecentAttack(long nowMillis) {
        return attackUntilMillis > nowMillis;
    }

    public boolean isPvp() {
        return pvp;
    }

    public boolean hasPendingCommand() {
        return pendingCommand != null;
    }

    public void resetTransient() {
        attackUntilMillis = 0L;
        pendingCommand = null;
    }

    public void resetAll() {
        resetTransient();
        pvp = false;
        pvpBossbarId = null;
        pvpClearAtMillis = 0L;
    }

    private Decision decide(long nowMillis) {
        if (pendingCommand == null) {
            return Decision.none();
        }
        if (pvp) {
            return Decision.showConfirm(takePendingCommand());
        }
        if (isRecentAttack(nowMillis)) {
            return Decision.none();
        }
        return Decision.send(takePendingCommand());
    }

    private void schedulePvpClear(long nowMillis, long clearDelayMillis) {
        if (!pvp) {
            pvpClearAtMillis = 0L;
            pvpBossbarId = null;
            return;
        }
        pvpClearAtMillis = safeAdd(nowMillis, Math.max(0L, clearDelayMillis));
    }

    private String takePendingCommand() {
        String command = pendingCommand;
        pendingCommand = null;
        return command;
    }

    private static long safeAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public enum DecisionType {
        NONE,
        SHOW_CONFIRM,
        SEND
    }

    public record Decision(DecisionType type, String command) {
        public Decision {
            Objects.requireNonNull(type, "type");
        }

        public static Decision none() {
            return new Decision(DecisionType.NONE, null);
        }

        public static Decision showConfirm(String command) {
            return new Decision(DecisionType.SHOW_CONFIRM, command);
        }

        public static Decision send(String command) {
            return new Decision(DecisionType.SEND, command);
        }
    }
}
