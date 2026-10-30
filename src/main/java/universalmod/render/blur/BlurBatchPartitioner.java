package universalmod.render.blur;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class BlurBatchPartitioner {
    private BlurBatchPartitioner() { }

    static List<List<BlurCaptureRequest>> partition(List<BlurCaptureRequest> requests, int capacity) {
        if (requests == null || requests.isEmpty()) return List.of();
        if (capacity < 1 || capacity > BlurUniformEncoder.MAX_RECTS) {
            throw new IllegalArgumentException("Invalid uniform-block rectangle capacity: " + capacity);
        }
        List<List<BlurCaptureRequest>> groups = new ArrayList<>();
        List<BlurCaptureRequest> current = new ArrayList<>();
        BlurCaptureRequest prior = null;
        for (BlurCaptureRequest request : requests) {

            if (request == null || !request.blur().visible()) continue;
            if (prior != null && (current.size() == capacity
                    || prior.blur().algorithm() != request.blur().algorithm()
                    || !Objects.equals(prior.scissorArea(), request.scissorArea()))) {
                groups.add(List.copyOf(current));
                current.clear();
            }
            current.add(request);
            prior = request;
        }
        if (!current.isEmpty()) groups.add(List.copyOf(current));
        return List.copyOf(groups);
    }
}
