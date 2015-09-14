package csdemo;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL4;
import static com.jogamp.opengl.GL4.*;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 *
 * @author xjurc
 */
public class GPUPerformance {
    
    private int addProgram;
    private int arrayProgram;
    
    private int aBuffer;
    private int bBuffer;
    private int resultBuffer;
    
    private int addElapsedQuery;
    private int arrayElapsedQuery;
    
    private boolean writeResults = false;
    private boolean writePerformanceInfo = false;
    
    // buffer indices for shaders
    private static final int A_BUFFER_INDEX = 0;
    private static final int B_BUFFER_INDEX = 1;
    private static final int RESULT_BUFFER_INDEX = 2;
    
    public void init(GL4 gl) {        
        // loading resources (shaders, data)
        try {
            addProgram = Utils.loadComputeProgram(gl, "/resources/shaders/add.glsl");
            arrayProgram = Utils.loadComputeProgram(gl, "/resources/shaders/array.glsl");
        } catch (IOException e) {
            System.err.println("Resource loading failed. " + e.getMessage());
            System.exit(1);
        }
        
        int buffers[] = new int[3];
        gl.glGenBuffers(3, buffers, 0);
        aBuffer = buffers[0];
        bBuffer = buffers[1];
        resultBuffer = buffers[2];
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, aBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, 512 * 256 * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, bBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, 512 * 256 * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, resultBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, 512 * 256 * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        // Bind buffer indices to programs
        Utils.bindShaderStorageBlock(gl, addProgram, "A", A_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, addProgram, "B", B_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, addProgram, "C", RESULT_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, arrayProgram, "Result", RESULT_BUFFER_INDEX);
        
        // timer queries
        int[] queries = new int[2];
        gl.glGenQueries(2, queries, 0);
        
        addElapsedQuery = queries[0];
        arrayElapsedQuery = queries[1];
    }
    
    public void profileArray(GL4 gl, int threadCount) {
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, A_BUFFER_INDEX, aBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, B_BUFFER_INDEX, bBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, RESULT_BUFFER_INDEX, resultBuffer);
        
        gl.glUseProgram(arrayProgram);
        
        Utils.setUniform(gl, arrayProgram, "elementCount", threadCount);
        Utils.setUniform(gl, arrayProgram, "operationCount", 512);
        
        gl.glBeginQuery(GL_TIME_ELAPSED, arrayElapsedQuery);
        gl.glDispatchCompute((threadCount + 255) / 256, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        gl.glUseProgram(addProgram);
        
        Utils.setUniform(gl, addProgram, "elementCount", 512 * 256);
        
        gl.glBeginQuery(GL_TIME_ELAPSED, addElapsedQuery);
        gl.glDispatchCompute((512 * 256 + 255) / 256, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        if (writePerformanceInfo) {
            writePerformanceInfo = false;
            // write primitive counts
            System.out.println("Thread count: " + threadCount);
            // write timer results
            IntBuffer timeBuffer = Buffers.newDirectIntBuffer(1);
            while (timeBuffer.get(0) != 1) {
                gl.glGetQueryObjectiv(addElapsedQuery, GL_QUERY_RESULT_AVAILABLE, timeBuffer);
            }
            // get the query result
            gl.glGetQueryObjectiv(arrayElapsedQuery, GL_QUERY_RESULT, timeBuffer);
            System.out.println("Time elapsed (GLSL: array): " + timeBuffer.get(0) / 1000000.0 + " ms");
            gl.glGetQueryObjectiv(addElapsedQuery, GL_QUERY_RESULT, timeBuffer);
            System.out.println("Time elapsed (GLSL: add): " + timeBuffer.get(0) / 1000000.0 + " ms");
        }
        
        if (writeResults) {
            writeResults = false;
            try {
                writeResult(gl);
            } catch (IOException ex) {
                ex.printStackTrace(System.err);
            }
        }
    }
    
    private void writeResult(GL4 gl) throws IOException {
        FloatBuffer result = Buffers.newDirectFloatBuffer(1);
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, resultBuffer);
        gl.glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, 4, result);
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        System.out.println("Array profiling result: " + result.get(0));
    }
    
    public void writeResults() {
        writeResults = true;
    }
    
    public void writePerformanceInfo() {
        writePerformanceInfo = true;
    }
    
}
