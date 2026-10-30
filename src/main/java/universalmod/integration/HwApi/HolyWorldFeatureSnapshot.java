package universalmod.integration.HwApi;

import java.util.Objects;
import java.util.Set;


public record HolyWorldFeatureSnapshot(
        HolyWorldFeatureState state,
        Set<String> blockedFeatures,
        long lastSuccessfulCheckMillis,
        long sessionGeneration,
        boolean channelAvailable,
        boolean enforcementActive,
        String lastError
) {
    public HolyWorldFeatureSnapshot {
        state = Objects.requireNonNull(state, "state");
        blockedFeatures = blockedFeatures == null ? Set.of() : Set.copyOf(blockedFeatures);
        lastError = lastError == null ? "" : lastError;
    }

    public static HolyWorldFeatureSnapshot disconnected(long generation) {
        return new HolyWorldFeatureSnapshot(
                HolyWorldFeatureState.UNKNOWN,
                Set.of(),
                0L,
                generation,
                false,
                false,
                ""
        );
    }
}
