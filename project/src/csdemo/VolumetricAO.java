package csdemo;

import com.jogamp.common.nio.Buffers;
import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.GL4;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class VolumetricAO {
    
    private int volumeProgram;
    
    private int atomsVolumeBuffer;
    
    private int volumeTexture;
    
    private int aoElapsedQuery;
    
    private boolean writeResults = false;
    private boolean writePerformanceInfo = false;
    
    private static final int VOLUME_SIZE = 8;
    private static final int VOXEL_COUNT = VOLUME_SIZE * VOLUME_SIZE * VOLUME_SIZE;
    
    private float voxelSize;
    private FloatBuffer atomsVolume;
    
    // buffer indices for shaders
    private static final int ATOMS_BUFFER_INDEX = 0;
    private static final int ATOMS_VOLUME_BUFFER_INDEX = 1;
    
    public float getLambda() {
        return 0.5f * voxelSize;
    }
    
    public float getVoxelSize() {
        return voxelSize;
    }
    
    public void init(GL4 gl) {
        // loading resources (shaders, data)
        try {
            volumeProgram = Utils.loadComputeProgram(gl, "/resources/shaders/ao/volume.glsl");
        } catch (IOException e) {
            System.err.println("Resource loading failed. " + e.getMessage());
            System.exit(1);
        }
        
        atomsVolume = Buffers.newDirectFloatBuffer(Scene.MAX_ATOMS);
        
        int buffers[] = new int[1];
        gl.glGenBuffers(1, buffers, 0);
        atomsVolumeBuffer = buffers[0];
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, atomsVolumeBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, Scene.MAX_ATOMS * Buffers.SIZEOF_FLOAT, null, GL_DYNAMIC_DRAW);
        
        int[] textures = new int[1];
        gl.glGenTextures(1, textures, 0);
        volumeTexture = textures[0];
        
        gl.glBindTexture(GL_TEXTURE_3D, volumeTexture);
        gl.glTexImage3D(GL_TEXTURE_3D, 0, GL_R32F, VOLUME_SIZE, VOLUME_SIZE, VOLUME_SIZE, 0, GL_RED, GL_FLOAT, null);
        gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        
        // Bind buffer indices to programs
        Utils.bindShaderStorageBlock(gl, volumeProgram, "Atoms", ATOMS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, volumeProgram, "AtomsVolume", ATOMS_VOLUME_BUFFER_INDEX);
        
        // timer query
        int[] queries = new int[1];
        gl.glGenQueries(1, queries, 0);
        
        aoElapsedQuery = queries[0];
    }
    
    public int ambientOcclusion(GL4 gl, int atomsBuffer, int atomCount, float aabbSize) {
        voxelSize = aabbSize / VOLUME_SIZE;
        
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ATOMS_BUFFER_INDEX, atomsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ATOMS_VOLUME_BUFFER_INDEX, atomsVolumeBuffer);
        
        gl.glUseProgram(volumeProgram);
        
        // clear volume
        gl.glClearTexImage(volumeTexture, 0, GL_RED, GL_FLOAT, null);
        gl.glBindImageTexture(0, volumeTexture, 0, true, 0, GL_READ_WRITE, GL_R32UI);
        
        Utils.setUniform(gl, volumeProgram, "atomCount", atomCount);
        Utils.setUniform(gl, volumeProgram, "voxelSize", voxelSize);
        Utils.setSampler(gl, volumeProgram, "volumeImg", 0);
        
        gl.glBeginQuery(GL_TIME_ELAPSED, aoElapsedQuery);
        gl.glDispatchCompute((atomCount + 255) / 256, 1, 1);
        gl.glMemoryBarrier(GL_TEXTURE_FETCH_BARRIER_BIT | GL_TEXTURE_UPDATE_BARRIER_BIT | (int) GL_ALL_BARRIER_BITS);
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        if (writePerformanceInfo) {
            writePerformanceInfo = false;
            // write timer results
            IntBuffer resultBuffer = Buffers.newDirectIntBuffer(1);
            while (resultBuffer.get(0) != 1) {
                gl.glGetQueryObjectiv(aoElapsedQuery, GL_QUERY_RESULT_AVAILABLE, resultBuffer);
            }
            // get the query result
            int aoElapsed = Utils.getTimeElapsed(gl.getGL2(), aoElapsedQuery);
            System.out.println("Time elapsed (AO): " + aoElapsed / 1000000.0 + " ms");
        }
        
        if (writeResults) {
            writeResults = false;
            try {
                writeVolume(gl);
            } catch (IOException ex) {
                ex.printStackTrace(System.err);
            }
        }
        
        return volumeTexture;
    }
    
    public void updateVolumes(GL4 gl, List<Atom> atoms) {
        for (int i = 0; i < atoms.size(); i++) {
            atomsVolume.put(i, atoms.get(i).v);
        }
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, atomsVolumeBuffer);
        gl.glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, atoms.size() * Buffers.SIZEOF_FLOAT, atomsVolume);
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    private void writeVolume(GL4 gl) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("volume.txt"))) {
            // get volume image
            FloatBuffer volume = Buffers.newDirectFloatBuffer(VOXEL_COUNT);
            for (int i = 0; i < VOXEL_COUNT; i++) {
                volume.put(i, -1f);
            }
            gl.glBindTexture(GL_TEXTURE_3D, volumeTexture);
            gl.glGetTexImage(GL_TEXTURE_3D, 0, GL_RED, GL_FLOAT, volume);
            // store to file
            for (int i = 0; i < VOXEL_COUNT; i++) {
                int x = i % VOLUME_SIZE;
                int y = (i / VOLUME_SIZE) % VOLUME_SIZE;
                int z = i / VOLUME_SIZE / VOLUME_SIZE;
                writer.append(String.format("[%2d, %2d, %2d]: %f", z, y, x, volume.get(i)));
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
