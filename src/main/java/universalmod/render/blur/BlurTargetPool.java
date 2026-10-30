package universalmod.render.blur;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BlurTargetPool<T> implements AutoCloseable {
    interface Factory<T> {
        T create(int width, int height);
        int width(T target);
        int height(T target);
        void resize(T target, int width, int height);
        void destroy(T target);
    }

    private final Factory<T> factory;
    private final List<T> down;
    private final List<T> up;
    private final LinkedHashMap<Object, T> results = new LinkedHashMap<>(16, .75f, true);
    private final int maxResults;
    private T crop;
    private boolean closed;

    BlurTargetPool(int maxIterations, int maxResults, Factory<T> factory) {
        if (maxIterations < 1 || maxResults < 1 || factory == null) {
            throw new IllegalArgumentException("Invalid blur target pool capacity/factory");
        }
        this.factory = factory;
        this.maxResults = maxResults;
        this.down = new ArrayList<>(maxIterations);
        this.up = new ArrayList<>(maxIterations);
        for (int i = 0; i < maxIterations; i++) {
            down.add(null);
            up.add(null);
        }
    }

    void ensure(int width, int height, float renderScale, int iterations) {
        checkOpen();
        if (width <= 0 || height <= 0 || !Float.isFinite(renderScale) || renderScale <= 0) return;
        int count = Math.min(Math.max(iterations, 0), down.size());
        int w = Math.max(Math.round(width * renderScale), 1);
        int h = Math.max(Math.round(height * renderScale), 1);
        for (int i = 0; i < count; ++i) {
            down.set(i, ensureTarget(down.get(i), w, h));
            if (i < count - 1) up.set(i, ensureTarget(up.get(i), w, h));
            w = Math.max(w / 2, 1);
            h = Math.max(h / 2, 1);
        }
    }

    T down(int index) {
        return down.get(index);
    }

    T up(int index) {
        return up.get(index);
    }

    T crop(int width, int height) {
        checkOpen();
        crop = ensureTarget(crop, width, height);
        return crop;
    }

    T result(Object key, int width, int height) {
        checkOpen();
        if (key == null || width < 1 || height < 1) return null;
        T target = ensureTarget(results.get(key), width, height);
        results.put(key, target);
        if (results.size() > maxResults) {
            var it = results.entrySet().iterator();
            Map.Entry<Object, T> oldest = it.next();
            it.remove();
            factory.destroy(oldest.getValue());
        }
        return target;
    }

    boolean hasResult(Object key, T target) {
        return target != null && results.get(key) == target;
    }

    int cachedResultCount() {
        return results.size();
    }

    private T ensureTarget(T target, int w, int h) {
        if (w < 1 || h < 1) throw new IllegalArgumentException("Invalid blur framebuffer dimensions");
        if (target == null) return factory.create(w, h);
        if (factory.width(target) != w || factory.height(target) != h) {
            factory.resize(target, w, h);
        }
        return target;
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("Blur target pool is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (crop != null) { factory.destroy(crop); crop = null; }
        for (int i = 0; i < down.size(); i++) {
            T d = down.set(i, null);
            if (d != null) factory.destroy(d);
            T u = up.set(i, null);
            if (u != null) factory.destroy(u);
        }
        for (T result : results.values()) factory.destroy(result);
        results.clear();
    }
}
