package universalmod.render.shape;

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
import java.util.ArrayList;
import java.util.List;

public final class ShapeBatchRenderer implements AutoCloseable {
    private static final int PARAM_BINDING = 6;
    private static final int STRIDE = 6 * Float.BYTES;
    private static final ShapeBatchRenderer INSTANCE = new ShapeBatchRenderer();

    private int rectangleProgram, outlineProgram, vao, vbo, paramsBuffer;
    private int rectangleCapacity, outlineCapacity;
    private int rectMatrixLocation, outlineMatrixLocation;
    private boolean initializationFailed;

    private ShapeBatchRenderer() { }
    public static ShapeBatchRenderer getInstance() { return INSTANCE; }

    public boolean drawRectangles(List<RectangleState> shapes) { return renderRectangles(shapes); }
    public boolean drawOutlines(List<OutlineState> shapes) { return renderOutlines(shapes); }

    private boolean renderRectangles(List<RectangleState> shapes) {
        if (shapes == null || shapes.isEmpty()) return true;
        if (initializationFailed) return false;
        try {
            RenderSystem.assertOnRenderThread();
            if (!ensurePrograms()) return false;
            for (int offset=0; offset<shapes.size(); offset+=rectangleCapacity) {
                List<RectangleState> sub=shapes.subList(offset,Math.min(shapes.size(),offset+rectangleCapacity));
                List<RectangleShape> values=new ArrayList<>(sub.size());
                for (RectangleState s:sub) {
                    if (s == null || !s.rectangle().visible()) return false;
                    values.add(s.rectangle());
                }
                ByteBuffer params=ShapeUniformEncoder.rectangles(values);
                FloatBuffer vertices=BufferUtils.createFloatBuffer(sub.size()*6*6);
                for (int i=0;i<sub.size();i++) {
                    RectangleState s=sub.get(i); RectangleShape r=s.rectangle();
                    quad(vertices,s.pose(),r.x(),r.y(),r.width(),r.height(),i);
                }
                vertices.flip();
                draw(rectangleProgram,rectMatrixLocation,vertices,params,sub.size());
            }
            return true;
        } catch (Exception | LinkageError e) {
            initializationFailed = true;
            return false;
        }
    }

    private boolean renderOutlines(List<OutlineState> shapes) {
        if (shapes==null || shapes.isEmpty()) return true;
        if (initializationFailed) return false;
        try {
            RenderSystem.assertOnRenderThread();
            if (!ensurePrograms()) return false;
            for(int offset=0;offset<shapes.size();offset+=outlineCapacity) {
                List<OutlineState> sub=shapes.subList(offset,Math.min(shapes.size(),offset+outlineCapacity));
                List<OutlineShape> values=new ArrayList<>(sub.size());
                for (OutlineState s:sub) {
                    if (s==null || !s.outline().visible()) return false;
                    values.add(s.outline());
                }
                ByteBuffer params=ShapeUniformEncoder.outlines(values);
                FloatBuffer vertices=BufferUtils.createFloatBuffer(sub.size()*6*6);
                for(int i=0;i<sub.size();i++) {
                    OutlineState s=sub.get(i); OutlineShape o=s.outline();
                    quad(vertices,s.pose(),o.x(),o.y(),o.width(),o.height(),i);
                }
                vertices.flip();
                draw(outlineProgram,outlineMatrixLocation,vertices,params,sub.size());
            }
            return true;
        } catch(Exception | LinkageError e) {
            initializationFailed=true;
            return false;
        }
    }

    private static void quad(FloatBuffer data, Matrix4f pose,float x,float y,float w,float h,int index) {
        Vector3f result=new Vector3f();
        vertex(data,pose,result,x,y,0,0,index);
        vertex(data,pose,result,x,y+h,0,1,index);
        vertex(data,pose,result,x+w,y+h,1,1,index);
        vertex(data,pose,result,x,y,0,0,index);
        vertex(data,pose,result,x+w,y+h,1,1,index);
        vertex(data,pose,result,x+w,y,1,0,index);
    }
    private static void vertex(FloatBuffer data,Matrix4f pose,Vector3f out,float x,float y,float u,float v,int i) {
        pose.transformPosition(x,y,0.0f,out);
        data.put(out.x).put(out.y).put(out.z).put(u).put(v).put(i);
    }

    private void draw(int program,int matrixLocation,FloatBuffer vertices,ByteBuffer params,int count) {

        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldArray=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int oldUniform=GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        int oldIndexedUniform=GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING,PARAM_BINDING);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
            new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).get(matrix);
            GL20.glUseProgram(program);
            GL20.glUniformMatrix4fv(matrixLocation,false,matrix);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,paramsBuffer);
            GL15.glBufferData(GL31.GL_UNIFORM_BUFFER,params,GL15.GL_STREAM_DRAW);
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,PARAM_BINDING,paramsBuffer);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STREAM_DRAW);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,count*6);
        } finally {
            GL14.glBlendFuncSeparate(srcRgb,dstRgb,srcA,dstA);
            setEnabled(GL11.GL_BLEND,blend);
            setEnabled(GL11.GL_DEPTH_TEST,depth);
            setEnabled(GL11.GL_CULL_FACE,cull);
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,PARAM_BINDING,oldIndexedUniform);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,oldUniform);
            GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldArray);
            GL20.glUseProgram(oldProgram);
        }
    }
    private static void setEnabled(int cap,boolean enabled) {
        if(enabled)GL11.glEnable(cap); else GL11.glDisable(cap);
    }

    private boolean ensurePrograms() throws IOException {
        if (rectangleProgram!=0 && outlineProgram!=0) return true;
        int block=GL11.glGetInteger(GL31.GL_MAX_UNIFORM_BLOCK_SIZE);
        rectangleCapacity=Math.min(ShapeUniformEncoder.MAX_SHAPES,block/ShapeUniformEncoder.RECTANGLE_BYTES);
        outlineCapacity=Math.min(ShapeUniformEncoder.MAX_SHAPES,block/ShapeUniformEncoder.OUTLINE_BYTES);
        if (rectangleCapacity<1 || outlineCapacity<1) return false;
        int rectangle=compile("rect_batch.fsh",rectangleCapacity,true);
        int outline=0;
        try { outline=compile("outline_batch.fsh",outlineCapacity,false); }
        catch(IOException|RuntimeException e) { GL20.glDeleteProgram(rectangle);throw e; }
        int oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            vao=GL30.glGenVertexArrays(); vbo=GL15.glGenBuffers();paramsBuffer=GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
            for (int i=0;i<3;i++) GL20.glEnableVertexAttribArray(i);
            GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,STRIDE,0L);
            GL20.glVertexAttribPointer(1,2,GL11.GL_FLOAT,false,STRIDE,3L*Float.BYTES);
            GL20.glVertexAttribPointer(2,1,GL11.GL_FLOAT,false,STRIDE,5L*Float.BYTES);
            rectangleProgram=rectangle;outlineProgram=outline;
            rectMatrixLocation=GL20.glGetUniformLocation(rectangleProgram,"ProjectionMatrix");
            outlineMatrixLocation=GL20.glGetUniformLocation(outlineProgram,"ProjectionMatrix");
            return true;
        } finally {
            GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldBuffer);
        }
    }
    private static int compile(String fragment,int capacity,boolean rectangle) throws IOException {
        String root="/assets/universalmod/shaders/port/shape/";
        int v=shader(GL20.GL_VERTEX_SHADER,root+"shape_batch.vsh",0);
        int f=0,program=0;
        try {
            f=shader(GL20.GL_FRAGMENT_SHADER,root+fragment,capacity);
            program=GL20.glCreateProgram();
            GL20.glAttachShader(program,v);GL20.glAttachShader(program,f);
            GL20.glBindAttribLocation(program,0,"Position");
            GL20.glBindAttribLocation(program,1,"Coord");
            GL20.glBindAttribLocation(program,2,"ShapeIndex");
            GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            int block=GL31.glGetUniformBlockIndex(program,rectangle?"RectangleParamsArray":"OutlineParamsArray");
            if(block==GL31.GL_INVALID_INDEX)throw new IllegalStateException("Shape std140 block missing");
            GL31.glUniformBlockBinding(program,block,PARAM_BINDING);
            return program;
        } catch(RuntimeException|IOException e) {
            if(program!=0)GL20.glDeleteProgram(program);throw e;
        } finally {
            GL20.glDeleteShader(v); if(f!=0)GL20.glDeleteShader(f);
        }
    }
    private static int shader(int type,String resource,int cap) throws IOException {
        String code;
        try(InputStream in=ShapeBatchRenderer.class.getResourceAsStream(resource)) {
            if(in==null)throw new IOException("Missing shape shader: "+resource);
            code=new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
        if(cap>0)code=code.replace("#define SHAPE_CAPACITY 512","#define SHAPE_CAPACITY "+cap);
        int id=GL20.glCreateShader(type);
        GL20.glShaderSource(id,code);GL20.glCompileShader(id);
        if(GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS)==0) {
            String error=GL20.glGetShaderInfoLog(id); GL20.glDeleteShader(id);
            throw new IllegalStateException(resource+": "+error);
        }
        return id;
    }
    @Override public void close() {
        if(!RenderSystem.isOnRenderThread())return;
        if(vbo!=0)GL15.glDeleteBuffers(vbo);
        if(paramsBuffer!=0)GL15.glDeleteBuffers(paramsBuffer);
        if(vao!=0)GL30.glDeleteVertexArrays(vao);
        if(rectangleProgram!=0)GL20.glDeleteProgram(rectangleProgram);
        if(outlineProgram!=0)GL20.glDeleteProgram(outlineProgram);
        vao=vbo=paramsBuffer=rectangleProgram=outlineProgram=0;
        rectangleCapacity=outlineCapacity=0;
        initializationFailed=false;
    }
}
