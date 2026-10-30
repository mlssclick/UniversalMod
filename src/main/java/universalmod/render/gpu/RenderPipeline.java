package universalmod.render.gpu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class RenderPipeline implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("UniversalMod/GuiPipeline");
    private static final String DYNAMIC = "layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat; };";
    private static final String PROJECTION = "layout(std140) uniform Projection { mat4 ProjMat; };";
    private final String vertex, fragment;
    private final Runnable begin;
    private final Consumer<RenderPass> prepare;
    private int program;
    private int paramsVec4;
    private boolean blend = true;

    public RenderPipeline(String vertex, String fragment, Runnable begin, Consumer<RenderPass> prepare) {
        this.vertex = vertex; this.fragment = fragment; this.begin = begin; this.prepare = prepare;
    }
    public RenderPipeline(String vertex, String fragment) { this(vertex, fragment, () -> { }, pass -> { }); }
    public RenderPipeline withParamCapacity(int count) {
        if (program != 0 || count <= 0) throw new IllegalStateException("Invalid GUI parameter capacity");
        paramsVec4 = count;
        return this;
    }
    public RenderPipeline withBlend(boolean enabled) { blend = enabled; return this; }
    boolean blend() { return blend; }
    void begin() { begin.run(); }
    void prepare(RenderPass pass) { prepare.accept(pass); }

    int program() {
        if (program != 0) return program;
        int vs = 0, fs = 0, candidate = 0;
        try {
            vs = compile(GL20.GL_VERTEX_SHADER, source(vertex + ".vsh"));
            fs = compile(GL20.GL_FRAGMENT_SHADER, source(fragment + ".fsh"));
            candidate = GL20.glCreateProgram();
            GL20.glAttachShader(candidate, vs); GL20.glAttachShader(candidate, fs);
            String[] attributes = {"Position", "Color", "UV0", "UV1", "UV2", "Normal", "LineWidth"};
            for (int i = 0; i < attributes.length; i++) GL20.glBindAttribLocation(candidate, i, attributes[i]);
            GL20.glLinkProgram(candidate);
            if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate));
            program = candidate; candidate = 0;
            return program;
        } catch (IOException | RuntimeException failure) {
            LOG.error("Cannot create GUI pipeline {} / {}", vertex, fragment, failure);
            throw new IllegalStateException("GUI pipeline failed: " + vertex, failure);
        } finally {
            if (candidate != 0) GL20.glDeleteProgram(candidate);
            if (vs != 0) GL20.glDeleteShader(vs);
            if (fs != 0) GL20.glDeleteShader(fs);
        }
    }

    private String source(String path) throws IOException {
        String source = read(path);
        return paramsVec4 == 0 ? source : source.replaceAll("vec4 params\\[\\d+\\]", "vec4 params[" + paramsVec4 + "]");
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 0) return shader;
        String error = GL20.glGetShaderInfoLog(shader); GL20.glDeleteShader(shader);
        throw new IllegalStateException(error);
    }

    private static String read(String path) throws IOException {
        Identifier id = Identifier.of("universalmod", "shaders/" + path);
        var resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
        try (InputStream stream = resource.isPresent() ? resource.get().getInputStream()
                : RenderPipeline.class.getResourceAsStream("/assets/universalmod/shaders/" + path)) {
            if (stream == null) throw new IOException("Missing shader: " + id);
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("#moj_import <minecraft:dynamictransforms.glsl>", DYNAMIC)
                    .replace("#moj_import <universalmod:dynamictransforms.glsl>", DYNAMIC)
                    .replace("#moj_import <minecraft:projection.glsl>", PROJECTION);
            if (source.contains("#moj_import <universalmod:common.glsl>"))
                source = source.replace("#moj_import <universalmod:common.glsl>", read("include/common.glsl"));
            return source;
        }
    }

    @Override public void close() {
        if (program != 0) { GL20.glDeleteProgram(program); program = 0; }
    }
}
