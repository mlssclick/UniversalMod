package universalmod.hud.element;

import universalmod.render.optimize.UiFrameContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

final class ListAnimator<K, V> {
    private static final float VISIBILITY_SPEED = 4.5F;
    private static final float POSITION_SPEED = 14.0F;
    private final LinkedHashMap<K, Row<K, V>> rows = new LinkedHashMap<>();
    private long lastNanos;
    private float extent;

    Collection<Row<K, V>> sync(List<V> values, Function<V, K> keyFunction, float rowHeight, float gap) {
        float dt = deltaSeconds();
        List<K> desiredOrder = new ArrayList<>(values.size());
        Set<K> desired = new HashSet<>();

        for (V value : values) {
            K key = keyFunction.apply(value);
            if (key == null || !desired.add(key)) {
                continue;
            }
            desiredOrder.add(key);
            Row<K, V> row = rows.get(key);
            if (row == null) {
                row = new Row<>(key, value);
                rows.put(key, row);
            } else {
                row.value = value;
            }
            row.visible = true;
        }

        for (Row<K, V> row : rows.values()) {
            if (!desired.contains(row.key)) {
                row.visible = false;
            }
        }

        reorder(desiredOrder, desired);

        Iterator<Row<K, V>> iterator = rows.values().iterator();
        while (iterator.hasNext()) {
            Row<K, V> row = iterator.next();
            float delta = dt * VISIBILITY_SPEED;
            row.rawProgress = row.visible
                    ? Math.min(1.0F, row.rawProgress + delta)
                    : Math.max(0.0F, row.rawProgress - delta);
            row.progress = cubicOut(row.rawProgress);
            if (!row.visible && row.rawProgress <= 0.001F) {
                iterator.remove();
            }
        }

        float cursor = 0.0F;
        float animatedExtent = 0.0F;
        boolean first = true;
        for (Row<K, V> row : rows.values()) {
            float progress = row.progress;
            if (!first) {
                cursor += gap * progress;
            }
            first = false;
            row.targetOffset = cursor;
            if (!row.positionInitialized) {
                row.offset = row.targetOffset;
                row.positionInitialized = true;
            } else {
                row.offset += (row.targetOffset - row.offset) * Math.min(1.0F, dt * POSITION_SPEED);
                if (Math.abs(row.targetOffset - row.offset) < 0.001F) {
                    row.offset = row.targetOffset;
                }
            }
            animatedExtent = Math.max(animatedExtent, row.offset + rowHeight * progress);
            cursor += rowHeight * progress;
        }
        extent = animatedExtent;
        return rows.values();
    }


    void clear() {
        rows.clear();
        extent = 0.0F;
        lastNanos = 0L;
    }

    float extent() {
        return extent;
    }

    boolean hasRows() {
        return !rows.isEmpty();
    }

    private void reorder(List<K> desiredOrder, Set<K> desired) {
        if (rows.isEmpty()) {
            return;
        }

        LinkedHashMap<K, Row<K, V>> reordered = new LinkedHashMap<>(rows.size());
        for (K key : desiredOrder) {
            Row<K, V> row = rows.get(key);
            if (row != null) {
                reordered.put(key, row);
            }
        }
        for (var entry : rows.entrySet()) {
            reordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        rows.clear();
        rows.putAll(reordered);
    }

    private float deltaSeconds() {
        long now = UiFrameContext.nowNanos();
        if (lastNanos == 0L) {
            lastNanos = now;
            return 1.0F / 60.0F;
        }
        float dt = Math.min(0.1F, Math.max(0.0F, (now - lastNanos) / 1_000_000_000.0F));
        lastNanos = now;
        return dt;
    }

    private static float cubicOut(float value) {
        float v = Math.max(0.0F, Math.min(1.0F, value));
        float inverse = 1.0F - v;
        return 1.0F - inverse * inverse * inverse;
    }

    static final class Row<K, V> {
        private final K key;
        private V value;
        private boolean visible;
        private float rawProgress;
        private float progress;
        private float offset;
        private float targetOffset;
        private boolean positionInitialized;

        private Row(K key, V value) {
            this.key = key;
            this.value = value;
        }

        V value() {
            return value;
        }

        float progress() {
            return progress;
        }

        float offset() {
            return offset;
        }

        float scale() {
            return 0.85F + 0.15F * progress;
        }
    }
}
