package universalmod.event.type;

import net.minecraft.screen.slot.SlotActionType;
import universalmod.event.CancellableEvent;

public final class ClickSlotEvent extends CancellableEvent {
    private final int syncId, slotId, button;
    private final SlotActionType actionType;

    public ClickSlotEvent(int syncId, int slotId, int button, SlotActionType actionType) {
        this.syncId = syncId;
        this.slotId = slotId;
        this.button = button;
        this.actionType = actionType;
    }

    public int getSyncId() {
        return syncId;
    }

    public int getSlotId() {
        return slotId;
    }

    public int getButton() {
        return button;
    }

    public SlotActionType getActionType() {
        return actionType;
    }
}
