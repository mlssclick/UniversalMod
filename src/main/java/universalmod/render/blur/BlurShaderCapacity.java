package universalmod.render.blur;

final class BlurShaderCapacity {
    private BlurShaderCapacity() { }

    static int rectangles(int maxUniformBlockBytes) {
        return Math.min(BlurUniformEncoder.MAX_RECTS,
                Math.max(0, maxUniformBlockBytes / BlurUniformEncoder.BYTES_PER_RECT));
    }

    static String specialize(String shader, int capacity) {
        if (capacity < 1 || capacity > BlurUniformEncoder.MAX_RECTS) {
            throw new IllegalArgumentException("Invalid blur GPU capacity: " + capacity);
        }
        String source = "vec4 params[3072];";
        if (!shader.contains(source)) {
            throw new IllegalArgumentException("Unexpected original BlurParamsArray shader layout");
        }
        return shader.replace(source, "vec4 params[" + (capacity * 3) + "];");
    }
}
