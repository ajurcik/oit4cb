package csdemo;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GL4bc;
import static com.jogamp.opengl.GL4bc.*;
import java.io.IOException;
import java.nio.FloatBuffer;

/**
 *
 * @author Adam
 */
public class Box {
    
    private int boxProgram;
    
    private int boxArrayBuffer;
    
    public void init(GL4 gl, int fragmentsBufferIndex, int fragmentsIndexBufferIndex) {
        // loading resources (shaders, data)
        try {
            boxProgram = Utils.loadProgram(gl, "/resources/shaders/ray/box.vert",
                    "/resources/shaders/ray/box.geom", "/resources/shaders/ray/box.frag");
        } catch (IOException e) {
            System.err.println("Resource loading failed. " + e.getMessage());
            System.exit(1);
        }
        
        int[] buffers = new int[1];
        gl.glGenBuffers(1, buffers, 0);
        boxArrayBuffer = buffers[0];
        
        FloatBuffer boxVertexData = Buffers.newDirectFloatBuffer(3);
        boxVertexData.put(0f);
        boxVertexData.put(0f);
        boxVertexData.put(0f);
        boxVertexData.rewind();
        
        gl.glBindBuffer(GL_ARRAY_BUFFER, boxArrayBuffer);
        gl.glBufferData(GL_ARRAY_BUFFER, 3 * Buffers.SIZEOF_FLOAT, boxVertexData, GL_STATIC_DRAW);
        
        Utils.bindShaderStorageBlock(gl, boxProgram, "ABuffer", fragmentsBufferIndex);
        Utils.bindShaderStorageBlock(gl, boxProgram, "ABufferIndex", fragmentsIndexBufferIndex);
    }
    
    public void render(GL4bc gl) {
        gl.glUseProgram(boxProgram);
        
        int[] viewport = new int[4];
        gl.glGetIntegerv(GL_VIEWPORT, viewport, 0);
        Utils.setUniform(gl, boxProgram, "viewport", 0f, 0f, 2f / viewport[2], 2f / viewport[3]);
        Utils.setUniform(gl, boxProgram, "window", viewport[2], viewport[3]);
        
        gl.glEnableClientState(GL_VERTEX_ARRAY);
        /*gl.glClientActiveTexture(GL_TEXTURE0);
        gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE1);
        gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE2);
        gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);*/
        
        gl.glBindBuffer(GL_ARRAY_BUFFER, boxArrayBuffer);
        gl.glVertexPointer(3, GL_FLOAT, 12, 0);
        /*gl.glClientActiveTexture(GL_TEXTURE0);
        gl.glTexCoordPointer(4, GL_FLOAT, 64, 16);
        gl.glClientActiveTexture(GL_TEXTURE1);
        gl.glTexCoordPointer(4, GL_FLOAT, 64, 32);
        gl.glClientActiveTexture(GL_TEXTURE2);
        gl.glTexCoordPointer(4, GL_FLOAT, 64, 48);*/
        
        gl.glDrawArrays(GL_POINTS, 0, 1);    
        
        gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        gl.glDisableClientState(GL_VERTEX_ARRAY);
        /*gl.glClientActiveTexture(GL_TEXTURE0);
        gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE1);
        gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE2);
        gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);*/
        
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT); // A-buffer
    }
    
}
