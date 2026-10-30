package universalmod.event.type;

import net.minecraft.entity.Entity;
import universalmod.event.Event;

public record AttackEntityEvent(Entity target) implements Event {
}
