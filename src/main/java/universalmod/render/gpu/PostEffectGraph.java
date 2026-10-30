package universalmod.render.gpu;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.BufferUtils;

import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class PostEffectGraph implements AutoCloseable {
    private record Block(String name,GpuUniformBuffer buffer,ByteBuffer data) {
        void bind(RenderPass pass) {
            if(!buffer.write(data.duplicate()))throw new IllegalStateException("Post-effect uniform upload failed: "+name);
            pass.setUniform(name,buffer.buffer());
        }
    }
    private record Pass(RenderPipeline pipeline,String input,String output,boolean nearest,List<Block> blocks) { }
    private final Map<String,SimpleFramebuffer> targets=new LinkedHashMap<>();
    private final List<Pass> passes=new ArrayList<>();
    private final GpuUniformBuffer sampler=new GpuUniformBuffer("PostEffect SamplerInfo",16);

    private PostEffectGraph() { }

    public static PostEffectGraph load(String name) {
        Identifier id=Identifier.of("universalmod","renderpasses/"+name+".json");
        PostEffectGraph graph=new PostEffectGraph();
        try(var stream=MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(id).getInputStream();
            var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            root.getAsJsonObject("targets").keySet().forEach(target->graph.targets.put(target,null));
            for(JsonElement element:root.getAsJsonArray("passes"))graph.addPass(element.getAsJsonObject());
            if(graph.passes.isEmpty())throw new IllegalArgumentException("Empty post-effect graph");
            return graph;
        } catch(Exception failure) {
            graph.close();
            throw new IllegalStateException("Cannot load post-effect graph "+id,failure);
        }
    }

    private void addPass(JsonObject definition) {
        JsonArray inputs=definition.getAsJsonArray("inputs");
        if(inputs.size()!=1)throw new IllegalArgumentException("Post-effect requires one source texture");
        JsonObject input=inputs.get(0).getAsJsonObject();
        List<Block> blocks=new ArrayList<>();
        if(definition.has("uniforms"))definition.getAsJsonObject("uniforms").entrySet().forEach(entry -> {
            ByteBuffer data=pack(entry.getValue().getAsJsonArray());
            blocks.add(new Block(entry.getKey(),new GpuUniformBuffer(entry.getKey(),data.remaining()),data));
        });
        String samplerName=input.get("sampler_name").getAsString()+"Sampler";
        String vertex=shader(definition.get("vertex_shader").getAsString());
        String fragment=shader(definition.get("fragment_shader").getAsString());
        RenderPipeline pipeline=new RenderPipeline(vertex,fragment,()->{},pass -> {
            pass.sampler(samplerName,0);
            pass.setUniform("SamplerInfo",sampler.buffer());
            blocks.forEach(block->block.bind(pass));
        }).withBlend(false);
        passes.add(new Pass(pipeline,input.get("target").getAsString(),definition.get("output").getAsString(),
                !input.has("bilinear") || !input.get("bilinear").getAsBoolean(),blocks));
    }

    private static String shader(String identifier) {
        if(identifier.equals("minecraft:core/screenquad"))return "post/legacy_fullscreen";
        if(identifier.equals("minecraft:post/blit"))return "post/legacy_graph_blit";
        if(identifier.startsWith("universalmod:"))return identifier.substring("universalmod:".length());
        throw new IllegalArgumentException("Unsupported post-effect shader "+identifier);
    }

    private static ByteBuffer pack(JsonArray uniforms) {
        record Field(int offset,String type,JsonArray values) { }
        List<Field> fields=new ArrayList<>();int size=0;
        for(JsonElement uniform:uniforms) {
            JsonObject value=uniform.getAsJsonObject();String type=value.get("type").getAsString();
            int count=switch(type) { case "float","int"->1;case "vec2"->2;case "vec3"->3;case "vec4"->4;case "mat4"->16;default->throw new IllegalArgumentException("Unsupported uniform type "+type); };
            int alignment=count==1?4:count==2?8:16;
            size=(size+alignment-1)/alignment*alignment;
            JsonArray values=value.getAsJsonArray("value");
            if(values.size()!=count)throw new IllegalArgumentException("Uniform "+value.get("name")+" expects "+count+" values");
            fields.add(new Field(size,type,values));size+=count*4;
        }
        ByteBuffer result=BufferUtils.createByteBuffer(Math.max(16,(size+15)/16*16));
        for(Field field:fields)for(int i=0;i<field.values.size();i++) {
            if(field.type.equals("int"))result.putInt(field.offset+i*4,field.values.get(i).getAsInt());
            else result.putFloat(field.offset+i*4,field.values.get(i).getAsFloat());
        }
        return result;
    }

    public Framebuffer process(Framebuffer source) {
        int width=source.textureWidth,height=source.textureHeight;
        if(width<=0 || height<=0)throw new IllegalArgumentException("Invalid post-effect source dimensions");
        for(var entry:targets.entrySet()) {
            SimpleFramebuffer target=entry.getValue();
            if(target==null) { target=new SimpleFramebuffer(width,height,false);entry.setValue(target); }
            else if(target.textureWidth!=width || target.textureHeight!=height)target.resize(width,height);
        }
        var info=BufferUtils.createByteBuffer(16);IndexedGuiBatch.vec4(info,0,width,height,width,height);
        if(!sampler.write(info))throw new IllegalStateException("Post-effect SamplerInfo upload failed");
        Framebuffer result=source;
        for(Pass pass:passes) {
            Framebuffer input=resolve(pass.input,source),output=resolve(pass.output,source);
            if(input==output)throw new IllegalStateException("Post-effect framebuffer feedback: "+pass.output);
            output.beginWrite(true);
            GuiStateRenderer.getInstance().submitGuiElement(new FullscreenState(pass.pipeline,input.getColorAttachment(),pass.nearest));
            result=output;
        }
        return result;
    }

    private Framebuffer resolve(String name,Framebuffer source) {
        if(name.equals("minecraft:main") || name.equals("minecraft:entity_outline"))return source;
        Framebuffer target=targets.get(name);
        if(target==null)throw new IllegalStateException("Unknown post-effect target "+name);
        return target;
    }

    public record FullscreenState(RenderPipeline pipeline,int texture,boolean nearest) implements GuiElementRenderState {
        @Override public void buildVertices(GuiVertexConsumer vertices) {
            var pose=new Matrix3x2f();
            vertices.addVertexWith2DPose(pose,-1,-1).setUv(0,0);vertices.addVertexWith2DPose(pose,-1,1).setUv(0,1);
            vertices.addVertexWith2DPose(pose,1,1).setUv(1,1);vertices.addVertexWith2DPose(pose,1,-1).setUv(1,0);
        }
        @Override public TextureSetup textureSetup() { return TextureSetup.single(()->texture,nearest); }
        @Override public ScreenRectangle scissorArea() { return null; }
        @Override public ScreenRectangle bounds() { return new ScreenRectangle(0,0,1,1); }
    }

    @Override public void close() {
        targets.values().stream().filter(Objects::nonNull).forEach(Framebuffer::delete);targets.replaceAll((name,target)->null);
        for(Pass pass:passes) { pass.pipeline.close();pass.blocks.forEach(block->block.buffer.close()); }
        sampler.close();passes.clear();
    }
}
