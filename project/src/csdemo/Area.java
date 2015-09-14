package csdemo;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL4;
import static com.jogamp.opengl.GL4.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 *
 * @author xjurc
 */
public class Area {
    
    private int areasProgram;
    private int minmaxProgram;
    
    private int areasBuffer;
    private int minmaxBuffer;
    
    private int areasTex;
    
    private boolean writeResults = false;
    private boolean writePerformanceInfo = false;
    
    // buffer indices for shaders
    private static final int TRIANGLES_BUFFER_INDEX = 0;
    private static final int AREAS_BUFFER_INDEX = 1;
    private static final int MINMAX_BUFFER_INDEX = 2;
    
    private int areaElapsedQuery;
    private int minmaxElapsedQuery;
    
    private static final FloatBuffer MINMAX_DATA = Buffers.newDirectFloatBuffer(4);
    
    private final Result RESULT = new Result();
    
    public void init(GL4 gl) {    
        // loading resources (shaders, data)
        try {
            areasProgram = Utils.loadComputeProgram(gl, "/resources/shaders/area/areas.glsl");
            minmaxProgram = Utils.loadComputeProgram(gl, "/resources/shaders/area/minmax.glsl");
        } catch (IOException e) {
            System.err.println("Resource loading failed. " + e.getMessage());
            System.exit(1);
        }
        
        int buffers[] = new int[2];
        gl.glGenBuffers(2, buffers, 0);
        areasBuffer = buffers[0];
        minmaxBuffer = buffers[1];
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, areasBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, GPUGraph.MAX_LABEL_COUNT * Buffers.SIZEOF_FLOAT, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, minmaxBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, 4 * Buffers.SIZEOF_FLOAT, null, GL_DYNAMIC_READ);
        
        int[] textures = new int[1];
        gl.glGenTextures(1, textures, 0);
        areasTex = textures[0];
        
        gl.glBindTexture(GL_TEXTURE_1D, areasTex);
        gl.glTexImage1D(GL_TEXTURE_1D, 0, GL_R32F, GPUGraph.MAX_LABEL_COUNT, 0, GL_RED, GL_FLOAT, null);
        gl.glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        gl.glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        gl.glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        
        // Bind buffer indices to programs
        Utils.bindShaderStorageBlock(gl, areasProgram, "Triangles", TRIANGLES_BUFFER_INDEX);
        //Utils.bindShaderStorageBlock(gl, areaProgram, "Areas", AREAS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, minmaxProgram, "MinMax", MINMAX_BUFFER_INDEX);
        
        // timer query
        int[] queries = new int[2];
        gl.glGenQueries(2, queries, 0);
        
        areaElapsedQuery = queries[0];
        minmaxElapsedQuery = queries[1];
    }
    
    public Result computeArea(GL4 gl, int trianglesArrayBuffer, int triangleCount, int labelsTex, int labelCount) {
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TRIANGLES_BUFFER_INDEX, trianglesArrayBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, AREAS_BUFFER_INDEX, areasBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MINMAX_BUFFER_INDEX, minmaxBuffer);
        
        gl.glUseProgram(areasProgram);
        
        // clear areas
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, areasBuffer);
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32F, GL_RED, GL_FLOAT, FloatBuffer.wrap(new float[] { 0f }));
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        gl.glClearTexImage(areasTex, 0, GL_RED, GL_FLOAT, null);
        gl.glBindImageTexture(0, areasTex, 0, true, 0, GL_READ_WRITE, GL_R32UI);
        
        gl.glActiveTexture(GL_TEXTURE1);
        gl.glBindTexture(GL_TEXTURE_BUFFER, labelsTex);
        
        Utils.setUniform(gl, areasProgram, "triangleCount", triangleCount);
        Utils.setSampler(gl, areasProgram, "areasImg", 0);
        Utils.setSampler(gl, areasProgram, "labelsTex", 1);
        
        gl.glBeginQuery(GL_TIME_ELAPSED, areaElapsedQuery);
        gl.glDispatchCompute((triangleCount + 255) / 256, 1, 1);
        gl.glMemoryBarrier(GL_TEXTURE_FETCH_BARRIER_BIT | GL_TEXTURE_UPDATE_BARRIER_BIT);
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        gl.glUseProgram(minmaxProgram);
        
        gl.glBindImageTexture(0, areasTex, 0, true, 0, GL_READ_ONLY, GL_R32F);
        
        Utils.setUniform(gl, minmaxProgram, "labelCount", labelCount);
        Utils.setSampler(gl, minmaxProgram, "areasImg", 0);
        
        gl.glBeginQuery(GL_TIME_ELAPSED, minmaxElapsedQuery);
        gl.glDispatchCompute(1, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT | GL_UNIFORM_BARRIER_BIT);
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        // get min max areas
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, minmaxBuffer);
        gl.glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, 3 * Buffers.SIZEOF_FLOAT, MINMAX_DATA);
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        if (writePerformanceInfo) {
            writePerformanceInfo = false;
            // write timer results
            IntBuffer resultBuffer = Buffers.newDirectIntBuffer(1);
            while (resultBuffer.get(0) != 1) {
                gl.glGetQueryObjectiv(minmaxElapsedQuery, GL_QUERY_RESULT_AVAILABLE, resultBuffer);
            }
            // get the query result
            gl.glGetQueryObjectiv(areaElapsedQuery, GL_QUERY_RESULT, resultBuffer);
            System.out.println("Time elapsed (area): " + resultBuffer.get(0) / 1000000.0 + " ms");
            gl.glGetQueryObjectiv(minmaxElapsedQuery, GL_QUERY_RESULT, resultBuffer);
            System.out.println("Time elapsed (minmax): " + resultBuffer.get(0) / 1000000.0 + " ms");
        }
        
        if (writeResults) {
            writeResults = false;
            try {
                //writeAreas(gl);
                writeAreasTex(gl);
            } catch (IOException ex) {
                ex.printStackTrace(System.err);
            }
        }
        
        return RESULT;
    }
    
    /**
     * Interface to results
     */
    public class Result {
        
        public int getAreasTexture() {
            return Area.this.areasTex;
        }
        
        public int getMinMaxBuffer() {
            return Area.this.minmaxBuffer;
        }
        
    }
    
    private void writeAreas(GL4 gl) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("areas.txt"))) {
            writer.append(String.format("Min: %f", MINMAX_DATA.get(0)));
            writer.newLine();
            writer.append(String.format("Max: %f", MINMAX_DATA.get(1)));
            writer.newLine();
            writer.append(String.format("Max2: %f", MINMAX_DATA.get(2)));
            writer.newLine();
            writer.newLine();
            // map areas buffer
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, areasBuffer);
            FloatBuffer areas = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asFloatBuffer();
            // write areas
            for (int i = 0; i < GPUGraph.MAX_LABEL_COUNT; i++) {
                writer.append(String.format("(%2d): %f", i, areas.get(i)));
                writer.newLine();
            }
            // unmap areas buffer
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    private void writeAreasTex(GL4 gl) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("areas.txt"))) {
            writer.append(String.format("Min: %f", MINMAX_DATA.get(0)));
            writer.newLine();
            writer.append(String.format("Max: %f", MINMAX_DATA.get(1)));
            writer.newLine();
            writer.append(String.format("Max2: %f", MINMAX_DATA.get(2)));
            writer.newLine();
            writer.newLine();
            // get areas image
            FloatBuffer areas = Buffers.newDirectFloatBuffer(GPUGraph.MAX_LABEL_COUNT);
            gl.glBindTexture(GL_TEXTURE_1D, areasTex);
            gl.glGetTexImage(GL_TEXTURE_1D, 0, GL_RED, GL_FLOAT, areas);
            // store to file
            for (int i = 0; i < GPUGraph.MAX_LABEL_COUNT; i++) {
                writer.append(String.format("(%2d): %f", i, areas.get(i)));
                writer.newLine();
            }
        }
    }
    
    public void writeResults() {
        writeResults = true;
    }
    
    public void writePerformanceInfo() {
        writePerformanceInfo = true;
    }
    
}
