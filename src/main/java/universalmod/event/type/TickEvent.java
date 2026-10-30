package universalmod.event.type;

import net.minecraft.client.MinecraftClient;
import universalmod.event.Event;
import universalmod.event.EventPhase;

public abstract class TickEvent implements Event {
    private final MinecraftClient client;
    private final EventPhase phase;

    private TickEvent(MinecraftClient client, EventPhase phase) {
        this.client = client;
        this.phase = phase;
    }

    public MinecraftClient getClient() {
        return client;
    }

    public EventPhase getPhase() {
        return phase;
    }

    public boolean isPre() {
        return phase == EventPhase.PRE;
    }

    public boolean isPost() {
        return phase == EventPhase.POST;
    }

    public static final class Pre extends TickEvent {
        public Pre(MinecraftClient client) {
            super(client, EventPhase.PRE);
        }
    }

    public static final class Post extends TickEvent {
        public Post(MinecraftClient client) {
            super(client, EventPhase.POST);
        }
    }
}
