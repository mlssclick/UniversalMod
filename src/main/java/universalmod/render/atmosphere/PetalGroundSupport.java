package universalmod.render.atmosphere;

import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

final class PetalGroundSupport {
    private static final double EDGE_TOLERANCE = 0.00001;

    private PetalGroundSupport() {}

    static double topAt(VoxelShape shape, double x, double z) {
        double top = Double.NEGATIVE_INFINITY;
        for (Box box : shape.getBoundingBoxes()) {
            if (x >= box.minX - EDGE_TOLERANCE && x <= box.maxX + EDGE_TOLERANCE
                    && z >= box.minZ - EDGE_TOLERANCE && z <= box.maxZ + EDGE_TOLERANCE) {
                top = Math.max(top, box.maxY);
            }
        }
        return top;
    }
}
