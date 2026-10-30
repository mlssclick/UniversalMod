package universalmod.lifecycle.service;


import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;


public final class ClientLifecycleRegistry implements AutoCloseable {
    private final List<ClientService> services = new ArrayList<>();
    private final Set<ClientService> registered = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean started;
    private boolean closed;

    public synchronized <T extends ClientService> T register(T service) {
        if (service == null) {
            throw new IllegalArgumentException("service cannot be null");
        }
        if (closed) {
            throw new IllegalStateException("lifecycle registry is already closed");
        }
        if (!registered.add(service)) {
            return service;
        }
        services.add(service);
        if (started) {
            try {
                service.start();
            } catch (RuntimeException | LinkageError failure) {
                services.remove(service);
                registered.remove(service);
                try {
                    service.close();
                } catch (RuntimeException | LinkageError cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }
        return service;
    }

    public synchronized void start() {
        if (started || closed) {
            return;
        }
        started = true;
        try {
            for (ClientService service : services) {
                service.start();
            }
        } catch (RuntimeException | LinkageError failure) {
            try {
                close();
            } catch (RuntimeException | LinkageError cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<Throwable> failures = new ArrayList<>();
        for (int index = services.size() - 1; index >= 0; index--) {
            ClientService service = services.get(index);
            try {
                service.close();
            } catch (RuntimeException | LinkageError failure) {
                failures.add(failure);
            }
        }
        services.clear();
        registered.clear();
        if (!failures.isEmpty()) {
            IllegalStateException combined = new IllegalStateException("Failed to close "
                    + failures.size() + " client service(s)", failures.get(0));
            for (int i = 1; i < failures.size(); i++) {
                combined.addSuppressed(failures.get(i));
            }
            throw combined;
        }
    }
}
