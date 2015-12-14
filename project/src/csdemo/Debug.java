package csdemo;

import com.jogamp.opengl.GL4;
import static com.jogamp.opengl.GL4.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class Debug {
    
    public static void checkGridOverflow(GL4 gl, int gridCountsBuffer, int cellCount, int maxCellAtomCount) {
        // read counts
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, gridCountsBuffer);
        ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        int counts[] = new int[cellCount];
        data.asIntBuffer().get(counts);
        gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        // check counts
        for (int c = 0; c < cellCount; c++) {
            if (counts[c] >= maxCellAtomCount) {
                System.err.println("Warning: possible grid cell overflow. Cell: " + c + ", atom count: " + counts[c]);
            }
        }
    }
    
    public static void checkNeighborsOverflow(GL4 gl, int neighborCountsBuffer, int sphereCount, int maxNeighborCount) {
        // read counts
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, neighborCountsBuffer);
        ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        int counts[] = new int[sphereCount];
        data.asIntBuffer().get(counts);
        gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        // check counts
        for (int s = 0; s < sphereCount; s++) {
            if (counts[s] >= maxNeighborCount) {
                System.err.println("Warning: possible neighbors overflow. Sphere: " + s + ", neighbor count: " + counts[s]);
            }
        }
    }
    
    public static void checkArcsOverflow(GL4 gl, CLArcs arcs, int neighborCountsBuffer, int atomCount, int maxNeighborCount,
            int arcsCountBuffer, int maxArcsCount) {
        int maxCount = 0;
        // read counts
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, neighborCountsBuffer);
        ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
        int neighborCounts[] = new int[atomCount];
        data.asIntBuffer().get(neighborCounts);
        gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        // write arcs
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcsCountBuffer);
        IntBuffer arcsCounts = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
        for (int i = 0; i < atomCount; i++) {
            for (int j = 0; j < neighborCounts[i]; j++) {
                int count = arcsCounts.get(i * maxNeighborCount + j);
                if (count > maxArcsCount) {
                    System.err.println("Error: arcs overflow. Arc count: " + count);
                }
                maxCount = Math.max(maxCount, count);
            }
        }
        gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        //System.out.println("Max arc count: " + maxCount);

        //int threadCount = Utils.getCounter(gl, countersBuffer, 0);
        //System.out.println("Arcs threads finished: " + threadCount);
        //int hashCount = Utils.getCounter(gl, countersBuffer, 4);
        //System.out.println("Arc hashes written: " + hashCount);
        int hashErrorCount = arcs.getHashErrorCount();
        if (hashErrorCount > 0) {
            System.err.println("Error: arc hash overflow. Instance count: " + hashErrorCount);
        }
    }
    
}
