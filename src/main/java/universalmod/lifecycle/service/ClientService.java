package universalmod.lifecycle.service;

public interface ClientService extends AutoCloseable {
    default void start() {
    }

    @Override
    default void close() {
    }
}
