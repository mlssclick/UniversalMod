package universalmod.render.blur;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import universalmod.render.Render2DCoordinateSpace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class BlurBatchRenderer implements AutoCloseable {
    private static final int FLOATS_PER_VERTEX = 10;
    private static final int STRIDE = FLOATS_PER_VERTEX * Float.BYTES;
    private static final BlurBatchRenderer INSTANCE = new BlurBatchRenderer();

    private int program, vao, vertexBuffer, rectParamsBuffer, regionBuffer;
    private int projectionLocation;
    private int hardwareCapacity;
    private boolean programFailed;

    static BlurBatchRenderer instance() { return INSTANCE; }

    boolean render(List<BlurPass> blurs, int textureId, BlurRegion region) {
        if (blurs == null || blurs.isEmpty()) return true;
        if (textureId <= 0 || region == null || blurs.size() > BlurUniformEncoder.MAX_RECTS) return false;
        Matrix4f matrix = new Matrix4f(Render2DCoordinateSpace.matrix());
        List<BlurCaptureRequest> captures = new java.util.ArrayList<>(blurs.size());
        for (BlurPass b : blurs) {
            if (b == null) return false;
            captures.add(new BlurCaptureRequest(b, matrix, null));
        }
        return renderCaptured(captures, textureId, region);
    }

    boolean renderCaptured(List<BlurCaptureRequest> captures, int textureId, BlurRegion region) {
        if (captures == null || captures.isEmpty()) return true;
        if (textureId <= 0 || region == null || captures.size() > BlurUniformEncoder.MAX_RECTS) return false;
        if (programFailed) return false;
        try {
            RenderSystem.assertOnRenderThread();
            if (!ensureProgram()) return false;
            if (captures.size() > hardwareCapacity) return false;
        } catch (Throwable initFailure) {
            programFailed = true;
            return false;
        }

        FloatBuffer vertices = BufferUtils.createFloatBuffer(captures.size() * 6 * FLOATS_PER_VERTEX);
        Vector3f out = new Vector3f();
        for (int i = 0; i < captures.size(); i++) {
            BlurCaptureRequest request = captures.get(i);
            BlurPass blur = request.blur();
            Matrix4f pose = request.pose();
            if (blur == null || !blur.visible()) return false;
            float x = blur.x(), y = blur.y(), w = blur.width(), h = blur.height();
            int tl = blur.colorTopLeft(), tr = blur.colorTopRight();
            int br = blur.colorBottomRight(), bl = blur.colorBottomLeft();
            vertex(vertices, pose, out, x, y, 0, 0, tl, i);
            vertex(vertices, pose, out, x, y + h, 0, 1, bl, i);
            vertex(vertices, pose, out, x + w, y + h, 1, 1, br, i);
            vertex(vertices, pose, out, x, y, 0, 0, tl, i);
            vertex(vertices, pose, out, x + w, y + h, 1, 1, br, i);
            vertex(vertices, pose, out, x + w, y, 1, 0, tr, i);
        }
        vertices.flip();
        ByteBuffer params = BlurUniformEncoder.rects(captures.stream().map(BlurCaptureRequest::blur).toList());
        ByteBuffer regionBytes = BlurUniformEncoder.region(region.x(), region.y(), region.width(), region.height());
        FloatBuffer projection = BufferUtils.createFloatBuffer(16);
        new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).get(projection);

        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int oldUniformBuffer = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        int oldParamsBinding = GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, BlurPipelines.BLUR_PARAMS_BINDING);
        int oldRegionBinding = GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, BlurPipelines.BLUR_REGION_BINDING);
        int oldActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean oldBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean oldDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean oldCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            GL20.glUseProgram(program);
            GL20.glUniformMatrix4fv(projectionLocation, false, projection);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, rectParamsBuffer);
            GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, params, GL15.GL_STREAM_DRAW);
            GL31.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BlurPipelines.BLUR_PARAMS_BINDING, rectParamsBuffer);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, regionBuffer);
            GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, regionBytes, GL15.GL_STREAM_DRAW);
            GL31.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BlurPipelines.BLUR_REGION_BINDING, regionBuffer);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STREAM_DRAW);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, captures.size() * 6);
            return true;
        } finally {
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcA, dstA);
            setEnabled(GL11.GL_BLEND, oldBlend);
            setEnabled(GL11.GL_DEPTH_TEST, oldDepth);
            setEnabled(GL11.GL_CULL_FACE, oldCull);
            GL31.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BlurPipelines.BLUR_PARAMS_BINDING, oldParamsBinding);
            GL31.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BlurPipelines.BLUR_REGION_BINDING, oldRegionBinding);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, oldUniformBuffer);
            GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArray);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
            GL13.glActiveTexture(oldActiveTexture);
            GL20.glUseProgram(oldProgram);
        }
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability);
    }

    private static void vertex(FloatBuffer data, Matrix4f pose, Vector3f result,
                               float x, float y, float u, float v, int color, int index) {
        pose.transformPosition(x, y, 0, result);
        data.put(result.x).put(result.y).put(result.z);
        data.put(((color >>> 16) & 255) / 255f);
        data.put(((color >>> 8) & 255) / 255f);
        data.put((color & 255) / 255f);
        data.put(((color >>> 24) & 255) / 255f);
        data.put(u).put(v).put(index);
    }

    private boolean ensureProgram() throws IOException {
        if (program != 0) return true;
        hardwareCapacity = BlurShaderCapacity.rectangles(GL11.glGetInteger(GL31.GL_MAX_UNIFORM_BLOCK_SIZE));
        if (hardwareCapacity < 1) return false;
        int vert = shader(GL20.GL_VERTEX_SHADER, BlurPipelines.BATCH_VERTEX_RESOURCE);
        int frag = 0, linked = 0;
        try {
            frag = shader(GL20.GL_FRAGMENT_SHADER, BlurPipelines.BATCH_FRAGMENT_RESOURCE, hardwareCapacity);
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vert);
            GL20.glAttachShader(linked, frag);
            GL20.glBindAttribLocation(linked, 0, "Position");
            GL20.glBindAttribLocation(linked, 1, "Color");
            GL20.glBindAttribLocation(linked, 2, "Coord");
            GL20.glBindAttribLocation(linked, 3, "BatchIndex");
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(linked));
            }
            int paramsIndex = GL31.glGetUniformBlockIndex(linked, "BlurParamsArray");
            int regionIndex = GL31.glGetUniformBlockIndex(linked, "BlurRegion");
            if (paramsIndex == GL31.GL_INVALID_INDEX || regionIndex == GL31.GL_INVALID_INDEX) {
                throw new IllegalStateException("Missing original std140 uniform blocks");
            }
            GL31.glUniformBlockBinding(linked, paramsIndex, BlurPipelines.BLUR_PARAMS_BINDING);
            GL31.glUniformBlockBinding(linked, regionIndex, BlurPipelines.BLUR_REGION_BINDING);
            int lastProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GL20.glUseProgram(linked);
            GL20.glUniform1i(GL20.glGetUniformLocation(linked, "Sampler0"), 0);
            GL20.glUseProgram(lastProgram);
            projectionLocation = GL20.glGetUniformLocation(linked, "Projection");
            vao = GL30.glGenVertexArrays();
            vertexBuffer = GL15.glGenBuffers();
            rectParamsBuffer = GL15.glGenBuffers();
            regionBuffer = GL15.glGenBuffers();
            int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int oldVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, STRIDE, 7L * Float.BYTES);
            GL20.glEnableVertexAttribArray(3);
            GL20.glVertexAttribPointer(3, 1, GL11.GL_FLOAT, false, STRIDE, 9L * Float.BYTES);
            GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldVbo);
            program = linked;
            return true;
        } catch (RuntimeException | IOException e) {
            if (linked != 0) GL20.glDeleteProgram(linked);
            throw e;
        } finally {
            GL20.glDeleteShader(vert);
            if (frag != 0) GL20.glDeleteShader(frag);
        }
    }

    private static int shader(int type, String resource) throws IOException {
        return shader(type, resource, 0);
    }

    private static int shader(int type, String resource, int maxRects) throws IOException {
        String text;
        try (InputStream stream = BlurBatchRenderer.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing GPU shader " + resource);
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (maxRects != 0) text = BlurShaderCapacity.specialize(text, maxRects);
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, text);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String message = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(resource + ": " + message);
        }
        return shader;
    }

    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) return;
        if (vertexBuffer != 0) GL15.glDeleteBuffers(vertexBuffer);
        if (rectParamsBuffer != 0) GL15.glDeleteBuffers(rectParamsBuffer);
        if (regionBuffer != 0) GL15.glDeleteBuffers(regionBuffer);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (program != 0) GL20.glDeleteProgram(program);
        vertexBuffer = rectParamsBuffer = regionBuffer = vao = program = 0;
        projectionLocation = 0;
        hardwareCapacity = 0;
        programFailed = false;
    }
}
