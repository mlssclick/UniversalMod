package universalmod.render.blur;

import net.minecraft.client.gl.ShaderProgramKey;
import universalmod.render.ClientPipelines;

final class BlurPipelines {
    static final ShaderProgramKey BATCHED_FALLBACK = ClientPipelines.BLUR;
    static final ShaderProgramKey KAWASE_DOWN = ClientPipelines.KAWASE_DOWN;
    static final ShaderProgramKey KAWASE_UP = ClientPipelines.KAWASE_UP;
    static final ShaderProgramKey SCOREBOARD_CROP = ClientPipelines.SCOREBOARD_CROP;
    static final ShaderProgramKey SCOREBOARD_SEPARABLE = ClientPipelines.SCOREBOARD_SEPARABLE;
    static final int BLUR_PARAMS_BINDING = 0;
    static final int BLUR_REGION_BINDING = 1;
    static final String BATCH_VERTEX_RESOURCE = "/assets/universalmod/shaders/compat/batched_blur_1214.vsh";
    static final String BATCH_FRAGMENT_RESOURCE = "/assets/universalmod/shaders/compat/batched_blur_1214.fsh";
    private BlurPipelines() { }
}
