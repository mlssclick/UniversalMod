package universalmod.hud.notification;

import universalmod.module.Module;
import universalmod.module.ModuleCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
public final class HudNotificationManager {
    private static final HudNotificationManager INSTANCE = new HudNotificationManager();
    private static final int MAX_VISIBLE = 5;
    private static final long LIFE_MS = 3000L;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> renderSnapshot = new ArrayList<>(MAX_VISIBLE + 2);
    private final List<Entry> renderSnapshotView = Collections.unmodifiableList(renderSnapshot);
    private boolean ready;

    private HudNotificationManager() {
    }

    public static HudNotificationManager getInstance() {
        return INSTANCE;
    }

    public synchronized void setReady(boolean ready) {
        this.ready = ready;
        if (!ready) {
            entries.clear();
            renderSnapshot.clear();
        }
    }

    public synchronized void onModuleState(Module module, boolean enabled) {
        if (!ready || module == null) {
            return;
        }
        String raw = module.getName();
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("ClickGUI")) {
            return;
        }
        String display = module.getDisplayName();
        if (display == null || display.isBlank()) {
            display = raw;
        }
        entries.add(0, new Entry(display, module.getCategory(), enabled));

        int visible = 0;
        for (Entry entry : entries) {
            if (!entry.closing) {
                visible++;
                if (visible > MAX_VISIBLE) {
                    entry.closing = true;
                }
            }
        }
        while (entries.size() > 64) {
            entries.remove(entries.size() - 1);
        }
    }

    public synchronized List<Entry> snapshotAndUpdate(float stride) {
        long nowMs = System.currentTimeMillis();
        long nowNs = System.nanoTime();
        int slot = 0;
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (!entry.closing && nowMs - entry.createdAt >= LIFE_MS) {
                entry.closing = true;
            }
            entry.update(slot * stride, nowNs);
            if (!entry.closing || entry.visibility() > 0.001F) {
                slot++;
            }
            if (entry.closing && entry.rawVisibility <= 0.001F) {
                iterator.remove();
            }
        }
        renderSnapshot.clear();
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        renderSnapshot.addAll(entries);
        return renderSnapshotView;
    }

    public static final class Entry {
        private final String module;
        private final ModuleCategory category;
        private final boolean enabled;
        private final long createdAt = System.currentTimeMillis();
        private float rawVisibility;
        private float y;
        private long lastNanos;
        private boolean yInitialized;
        private boolean closing;

        private Entry(String module, ModuleCategory category, boolean enabled) {
            this.module = module;
            this.category = category;
            this.enabled = enabled;
        }

        private void update(float targetY, long nowNs) {
            float dt = lastNanos == 0L
                    ? 1.0F / 60.0F
                    : Math.min(0.1F, Math.max(0.0F, (nowNs - lastNanos) / 1_000_000_000.0F));
            lastNanos = nowNs;

            float delta = dt * 4.5F;
            rawVisibility = closing
                    ? Math.max(0.0F, rawVisibility - delta)
                    : Math.min(1.0F, rawVisibility + delta);

            if (!yInitialized) {
                y = targetY;
                yInitialized = true;
            } else {
                float difference = targetY - y;
                if (Math.abs(difference) < 0.05F) {
                    y = targetY;
                } else {
                    y += difference * Math.min(1.0F, dt * 14.0F);
                }
            }
        }

        public String module() {
            return module;
        }

        public ModuleCategory category() {
            return category;
        }

        public boolean enabled() {
            return enabled;
        }

        public float y() {
            return y;
        }

        public float visibility() {
            float p = Math.max(0.0F, Math.min(1.0F, rawVisibility));
            if (p < 0.5F) {
                return 4.0F * p * p * p;
            }
            float f = -2.0F * p + 2.0F;
            return 1.0F - f * f * f * 0.5F;
        }
    }
}
