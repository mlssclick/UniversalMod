package universalmod.module.utility;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class DonateComponentSlices {
    private DonateComponentSlices() {
    }

    static Component slice(Component component, int start, int end) {
        if (component == null || end <= start) return Component.empty();
        List<StyledText> segments = flatten(component);
        MutableComponent result = Component.empty();
        int index = 0;
        for (StyledText segment : segments) {
            int segmentStart = index;
            int segmentEnd = index + segment.text().length();
            if (segmentEnd <= start) { index = segmentEnd; continue; }
            if (segmentStart >= end) break;
            int localStart = Math.max(0, start - segmentStart);
            int localEnd = Math.min(segment.text().length(), end - segmentStart);
            if (localStart < localEnd) result.append(Component.literal(segment.text().substring(localStart, localEnd)).setStyle(segment.style()));
            index = segmentEnd;
        }
        return result;
    }

    static Style styleAt(Component component, int targetIndex) {
        int index = 0;
        for (StyledText segment : flatten(component)) {
            int end = index + segment.text().length();
            if (targetIndex >= index && targetIndex < end) return segment.style();
            index = end;
        }
        return component == null ? Style.EMPTY : component.getStyle();
    }

    private static List<StyledText> flatten(Component component) {
        List<StyledText> segments = new ArrayList<>();
        if (component == null) return segments;
        component.visit((style, text) -> {
            if (text != null && !text.isEmpty()) segments.add(new StyledText(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        if (segments.isEmpty() && !component.getString().isEmpty()) segments.add(new StyledText(component.getString(), component.getStyle()));
        return segments;
    }

    private record StyledText(String text, Style style) {
    }
}
