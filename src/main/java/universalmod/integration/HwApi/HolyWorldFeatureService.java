package universalmod.integration.HwApi;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import universalmod.event.EventBus;
import universalmod.event.SubscribeEvent;
import universalmod.event.type.TickEvent;
import universalmod.gui.clickgui.ClickGui;
import universalmod.lifecycle.service.ClientService;
import universalmod.module.Module;
import universalmod.module.ModuleManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class HolyWorldFeatureService implements ClientService {
    private static final String REQUEST_METHOD_NAME = "checkFeatures";
    private static final String HOLYWORLD_SEGMENT = "holyworld";
    private static final long REQUEST_COOLDOWN_MS = 10_000L;
    private static final String FIXED_CLIENT_ID = "universalmod";
    private static final long RETRY_INTERVAL_MS = 10_000L;
    private static final int MAX_RETRIES = 5;

    private final EventBus eventBus;
    private final ModuleManager moduleManager;
    private final HolyWorldFeaturePolicy policy = new HolyWorldFeaturePolicy();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Set<String> disabledFeatures = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastRequestAt = new AtomicLong(0L);
    private final AtomicBoolean receivedInitialResponse = new AtomicBoolean(false);
    private final AtomicReference<String> pendingRequestId = new AtomicReference<>(null);
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "HolyWorld-FeatureControl");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong sessionGeneration = new AtomicLong(0L);

    private ScheduledFuture<?> retryFuture;
    private volatile String lastObservedHost;
    private volatile boolean receiverRegistered;
    private volatile boolean closed;

    public HolyWorldFeatureService(EventBus eventBus, ModuleManager moduleManager) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.moduleManager = Objects.requireNonNull(moduleManager, "moduleManager");
        this.policy.bind(this::isFeatureDisabled, this::requestFeatureStatus);
    }

    public HolyWorldFeaturePolicy policy() {
        return policy;
    }

    public HolyWorldFeatureSnapshot snapshot() {
        return policy.snapshot();
    }

    @Override
    public void start() {
        if (closed || !initialized.compareAndSet(false, true)) {
            return;
        }

        receiverRegistered = ClientPlayNetworking.registerGlobalReceiver(
                HolyWorldLiteApiPayload.CHANNEL_ID,
                (client, handler, buffer, responseSender) -> {
                    
                    String json = HolyWorldLiteApiPayload.decode(buffer).json();
                    client.execute(() -> handlePayload(json));
                }
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            resetCache();
            client.execute(() -> {
                if (isHolyWorldServer()) {
                    publishChecking();
                    requestServerRules("");
                    startRetryTask();
                    enforceServerLocks();
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::resetCache));
        eventBus.register(this);
    }

    
    public void onHostChange() {
        if (isHolyWorldServer()) {
            if (!receivedInitialResponse.get() && pendingRequestId.get() == null) {
                publishChecking();
                requestServerRules("");
                startRetryTask();
                enforceServerLocks();
            }
        } else {
            resetCache();
        }
    }

    public synchronized void resetCache() {
        stopRetryTask();
        disabledFeatures.clear();
        receivedInitialResponse.set(false);
        pendingRequestId.set(null);
        retryCount.set(0);
        long generation = sessionGeneration.incrementAndGet();
        policy.publish(HolyWorldFeatureSnapshot.disconnected(generation));
        if (initialized.get() && !closed) {
            moduleManager.refreshAccessPolicy();
        }
    }

    private synchronized void startRetryTask() {
        stopRetryTask();
        retryCount.set(0);

        long now = System.currentTimeMillis();
        long sinceLastRequest = now - lastRequestAt.get();
        long initialDelay = sinceLastRequest < REQUEST_COOLDOWN_MS
                ? REQUEST_COOLDOWN_MS - sinceLastRequest + 100L
                : RETRY_INTERVAL_MS;

        retryFuture = scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!isHolyWorldServer() || receivedInitialResponse.get()) {
                    stopRetryTask();
                    return;
                }
                if (retryCount.incrementAndGet() > MAX_RETRIES) {
                    stopRetryTask();
                    return;
                }
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    client.execute(() -> {
                        if (isHolyWorldServer() && !receivedInitialResponse.get()) {
                            requestServerRules("");
                        }
                    });
                }
            } catch (Throwable ignored) {
                
            }
        }, initialDelay, RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopRetryTask() {
        if (retryFuture != null) {
            retryFuture.cancel(false);
            retryFuture = null;
        }
    }

    
    public boolean isFeatureDisabled(String feature) {
        if (!isHolyWorldServer()) {
            return false;
        }
        if (!receivedInitialResponse.get()) {
            return true;
        }
        return disabledFeatures.contains(HolyWorldFeatureId.normalize(feature));
    }

    public Set<String> getDisabledFeatures() {
        return Collections.unmodifiableSet(disabledFeatures);
    }

    
    public void requestFeatureStatus(String feature) {
        requestServerRules(feature);
    }

    private boolean requestServerRules(String requestedFeature) {
        if (!isHolyWorldServer()) {
            return false;
        }

        long now = System.currentTimeMillis();
        long previous = lastRequestAt.get();
        if (now - previous < REQUEST_COOLDOWN_MS) {
            return false;
        }
        if (!lastRequestAt.compareAndSet(previous, now)) {
            return false;
        }

        String requestId = UUID.randomUUID().toString();
        pendingRequestId.set(requestId);
        List<String> features = buildFeatureArray(requestedFeature);
        String json = HolyWorldLiteApiProtocol.checkFeaturesRequest(REQUEST_METHOD_NAME, requestId, FIXED_CLIENT_ID, features);

        publishChecking();
        try {
            ClientPlayNetworking.send(HolyWorldLiteApiPayload.CHANNEL_ID, new HolyWorldLiteApiPayload(json).encode());
            return true;
        } catch (Throwable ignored) {
            
            return false;
        }
    }

    private void handlePayload(String json) {
        if (!isHolyWorldServer()) {
            resetCache();
            return;
        }

        try {
            JsonObject root = HolyWorldLiteApiProtocol.parseObject(json).orElse(null);
            if (root == null) {
                return;
            }

            boolean hasOk = root.has("ok");
            boolean isPush = root.has("event") || root.has("type") || root.has("action")
                    || (!hasOk && root.has("payload"));
            Set<String> blocklist = null;

            if (hasOk) {
                if (!root.get("ok").getAsBoolean()) {
                    return;
                }

                String responseId = HolyWorldLiteApiProtocol.getString(root, "id");
                String pending = pendingRequestId.get();
                if (pending == null || !pending.equals(responseId)) {
                    return;
                }
                pendingRequestId.set(null);
                blocklist = HolyWorldLiteApiProtocol.parseBlocklist(root.get("payload")).orElse(null);
            } else if (isPush) {
                blocklist = HolyWorldLiteApiProtocol.parseBlocklist(root.get("payload")).orElse(null);
            }

            if (blocklist != null) {
                disabledFeatures.clear();
                disabledFeatures.addAll(blocklist);
                receivedInitialResponse.set(true);
                stopRetryTask();
                publishAvailable();
                enforceServerLocks();
            }
        } catch (Throwable ignored) {
            
        }
    }

    
    public void enforceServerLocks() {
        moduleManager.refreshAccessPolicy();
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                ClickGui.validateState(client);
            }
        } catch (Throwable ignored) {
            
        }
    }

    private List<String> buildFeatureArray(String requestedFeature) {
        ArrayList<String> features = new ArrayList<>();
        for (Module module : moduleManager.getRegisteredModules()) {
            if (module == null) {
                continue;
            }
            String identifier = HolyWorldFeatureId.normalize(module.getIdentifier());
            if (!identifier.isEmpty() && !features.contains(identifier)) {
                features.add(identifier);
            }
        }

        String extra = HolyWorldFeatureId.normalize(requestedFeature);
        if (!extra.isEmpty() && !features.contains(extra)) {
            features.add(extra);
        }
        return features;
    }

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        String host = HolyWorldServerUtil.getCurrentServerHost();
        if (!Objects.equals(host, lastObservedHost)) {
            lastObservedHost = host;
            onHostChange();
        }
        
        enforceServerLocks();
    }

    private boolean isHolyWorldServer() {
        return HolyWorldServerUtil.hasServerSegment(HOLYWORLD_SEGMENT);
    }

    private void publishChecking() {
        long generation = sessionGeneration.get();
        HolyWorldFeatureSnapshot previous = policy.snapshot();
        long lastSuccessfulCheckMillis = previous.sessionGeneration() == generation
                ? previous.lastSuccessfulCheckMillis()
                : 0L;
        policy.publish(new HolyWorldFeatureSnapshot(
                HolyWorldFeatureState.CHECKING,
                Set.copyOf(disabledFeatures),
                lastSuccessfulCheckMillis,
                generation,
                true,
                isHolyWorldServer(),
                ""
        ));
        moduleManager.refreshAccessPolicy();
    }

    private void publishAvailable() {
        long generation = sessionGeneration.get();
        policy.publish(new HolyWorldFeatureSnapshot(
                HolyWorldFeatureState.AVAILABLE,
                Set.copyOf(disabledFeatures),
                System.currentTimeMillis(),
                generation,
                true,
                true,
                ""
        ));
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        stopRetryTask();
        eventBus.unregister(this);
        if (receiverRegistered) {
            ClientPlayNetworking.unregisterGlobalReceiver(HolyWorldLiteApiPayload.CHANNEL_ID);
            receiverRegistered = false;
        }
        scheduler.shutdownNow();
        disabledFeatures.clear();
        pendingRequestId.set(null);
        receivedInitialResponse.set(false);
        retryCount.set(0);
        policy.bind(null, null);
    }
}
