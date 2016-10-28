package csdemo;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL4;
import static com.jogamp.opengl.GL4.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.vecmath.Vector4f;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class Debug {
    
    private File debugDir = new File("debug");
    
    private static Debug instance;
    
    public static Debug getInstance() {
        if (instance == null) {
            instance = new Debug();
        }
        return instance;
    }
    
    private Debug() {
        
    }
    
    public void makeDebugDir() {
        Date now = new Date();
        debugDir = new File("debug-" + now.getTime());
        debugDir.mkdir();
    }
    
    public File getDebugDir() {
        return debugDir;
    }
    
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
    
    public void writeGrid(String filename, GL4 gl, int gridCountsBuffer, int gridIndicesBuffer) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, filename)))) {
            // read counts
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, gridCountsBuffer);
            ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            int counts[] = new int[Scene.CELL_COUNT];
            data.asIntBuffer().get(counts);
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            // write counts and indices
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, gridIndicesBuffer);
            IntBuffer indices = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            Set<Integer> cellIndices = new TreeSet<>();
            for (int i = 0; i < Scene.CELL_COUNT; i++) {
                if (counts[i] > 0) {
                    int x = i / Scene.GRID_SIZE / Scene.GRID_SIZE;
                    int y = (i / Scene.GRID_SIZE) % Scene.GRID_SIZE;
                    int z = i % Scene.GRID_SIZE;
                    writer.append(String.format("[%2d,%2d,%2d] (%2d): ", x, y, z, counts[i]));
                    cellIndices.clear();
                    for (int j = 0; j < counts[i]; j++) {
                        cellIndices.add(indices.get(Scene.MAX_CELL_ATOMS * i + j));
                    }
                    for (Integer index : cellIndices) {
                        writer.append(String.format("%6d", index));
                    }
                    writer.newLine();
                }
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writeNeighbors(GL4 gl, int neighborsBuffer, int neighborCountsBuffer, int sphereCount,
            String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, filename)))) {
            // read counts
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, neighborCountsBuffer);
            ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            int counts[] = new int[sphereCount];
            data.asIntBuffer().get(counts);
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            // write counts and indices
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, neighborsBuffer);
            IntBuffer neighbors = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            Set<Integer> neighborIndices = new LinkedHashSet<>();
            for (int i = 0; i < sphereCount; i++) {
                if (counts[i] > 0) {
                    writer.append(String.format("%4d (%2d): ", i, counts[i]));
                    neighborIndices.clear();
                    for (int j = 0; j < counts[i]; j++) {
                        neighborIndices.add(neighbors.get(Scene.MAX_NEIGHBORS * i + j));
                    }
                    for (Integer index : neighborIndices) {
                        writer.append(String.format("%6d", index));
                    }
                    writer.newLine();
                }
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writeArcs(GL4 gl, int neighborsCountBuffer, int atomCount, int arcsCountBuffer) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, "arcs.txt")))) {
            // read counts
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, neighborsCountBuffer);
            ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            int neighborCounts[] = new int[atomCount];
            data.asIntBuffer().get(neighborCounts);
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            // write arcs
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcsCountBuffer);
            IntBuffer arcsCounts = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            for (int i = 0; i < atomCount; i++) {
                int totalArcs = 0;
                List<Integer> counts = new ArrayList<>();
                for (int j = 0; j < neighborCounts[i]; j++) {
                    int count = arcsCounts.get(i * Scene.MAX_NEIGHBORS + j);
                    counts.add(count);
                    totalArcs += count;
                }
                writer.append(String.format("%4d (%2d): ", i, totalArcs));
                for (int count : counts) {
                    writer.append(String.format("%3d", count));
                }
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    private static class Fragment implements Comparable<Fragment> {
        
        private final int color;
        private final float depth;

        public Fragment(int color, float depth) {
            this.color = color;
            this.depth = depth;
        }
        
        @Override
        public int compareTo(Fragment other) {
            return (int)Math.signum(depth - other.depth);
        }
        
    }
    
    public void writeFragments(GL4 gl, int fragmentsIndexBuffer, int fragmentsBuffer, int width, int height) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, "fragments.txt")))) {
            // write A-Buffer dimensions
            writer.append("width: " + width + ", height: " + height);
            writer.newLine();
            // read fragment indices
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, fragmentsIndexBuffer);
            ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            int indices[] = new int[Scene.MAX_ABUFFER_PIXELS];
            data.asIntBuffer().get(indices);
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            // write fragments
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, fragmentsBuffer);
            int stride = Scene.SIZEOF_FRAGMENT;
            ByteBuffer fragments = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            List<Fragment> frags = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    frags.clear();
                    int index = indices[y * width + x];
                    if (index == Scene.INVALID_INDEX) {
                        continue;
                    }
                    while (index != Scene.INVALID_INDEX) {
                        int color = fragments.getInt(index * stride);
                        float depth = fragments.getFloat(index * stride + 4);
                        frags.add(new Fragment(color, depth));
                        index = fragments.getInt(index * stride + 8);
                    }
                    Collections.sort(frags);
                    writer.append(String.format("[%4d,%4d]: ", x, y));
                    for (Fragment f : frags) {
                        writer.append(String.format("%08x@%f ", f.color, f.depth));
                    }
                    /*if (frags.size() > 0) {
                        writer.append(String.format("%08x@%f ", frags.get(0).color, frags.get(0).depth)); // DEBUG
                    }*/
                    writer.newLine();
                }
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writeArcHashes(GL4 gl, int arcsBuffer, int arcHashesBuffer, int atomCount) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, "hashes.txt")))) {
            // read arcs
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcsBuffer);
            ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            float arcs[] = new float[Scene.MAX_TOTAL_ARCS * 4];
            data.asFloatBuffer().get(arcs);
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            // write hashes
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcHashesBuffer);
            int stride = Scene.SIZEOF_HASH;
            ByteBuffer hashes = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < Scene.MAX_TOTAL_ARC_HASHES; i++) {
                int key = hashes.getInt(i * stride);
                if (key != Scene.INVALID_KEY) {
                    char primary = ((key & 0xf0000000) == 0) ? 'p' : 's';
                    int ai = (key & 0x0fffffff) / atomCount;
                    int aj = (key & 0x0fffffff) % atomCount;
                    int atomk = hashes.getInt(i * stride + 4);
                    int index = hashes.getInt(i * stride + 8);
                    writer.append(String.format("[%4d, %4d] (%c): %4d, %8d", ai, aj, primary, atomk, index));
                    float x = arcs[4 * index];
                    float y = arcs[4 * index + 1];
                    float z = arcs[4 * index + 2];
                    float k = arcs[4 * index + 3];
                    writer.append(String.format(" --> (%f %f %f %f)", x, y, z, k));
                } else {
                    writer.append("(EMPTY)");
                }
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writeAtomsVisible(GL4 gl, int atomsVisibleBuffer, int atomCount) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, "atoms.txt")))) {
            // write tori
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, atomsVisibleBuffer);
            IntBuffer atomsVisible = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            for (int i = 0; i < atomCount; i++) {
                int visible = atomsVisible.get(i);
                writer.append(String.format("%4d: ", i));
                if (visible == 1) {
                    writer.append("1");
                }
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writeTriangles(GL4 gl, int trianglesArrayBuffer, int count) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, "triangles.txt")))) {
            // write triangles
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, trianglesArrayBuffer);
            ByteBuffer triangleArray = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < count; i++) {
                Vector4f position = getVec4(triangleArray, i * Scene.SIZEOF_TRIANGLE);
                writer.append(String.format("%4d: ", i));
                writer.append(String.format("position: [%f %f %f %f]", position.x, position.y, position.z, position.w));
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writeTori(GL4 gl, int toriArrayBuffer, int count) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, "tori.txt")))) {
            // write tori
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, toriArrayBuffer);
            ByteBuffer toriArray = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < count; i++) {
                Vector4f position = getVec4(toriArray, i * Scene.SIZEOF_TORUS);
                float operation = toriArray.getFloat(i * Scene.SIZEOF_TORUS + 28);
                String op = (operation > 0f) ? "AND" : (operation < 0f) ? "OR " : "ISOLATED";
                Vector4f plane1 = getVec4(toriArray, i * Scene.SIZEOF_TORUS + 48);
                Vector4f plane2 = getVec4(toriArray, i * Scene.SIZEOF_TORUS + 64);
                writer.append(String.format("%4d: ", i));
                writer.append(String.format("center: [%f %f %f], R: %f, ", position.x, position.y, position.z, position.w));
                writer.append(String.format("op: %s, ", op));
                writer.append(String.format("plane1: [%f %f %f %f], ", plane1.x, plane1.y, plane1.z, plane1.w));
                writer.append(String.format("plane2: [%f %f %f %f]", plane2.x, plane2.y, plane2.z, plane2.w));
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writePolygons(GL4 gl, int spheresArrayBuffer, int count) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, "polygons.txt")))) {
            // write polygons
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, spheresArrayBuffer);
            ByteBuffer polygonsArray = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < count; i++) {
                int index = polygonsArray.getInt(i * Scene.SIZEOF_POLYGON + 16);
                int label = polygonsArray.getInt(i * Scene.SIZEOF_POLYGON + 20);
                int circleStart = polygonsArray.getInt(i * Scene.SIZEOF_POLYGON + 24);
                int circleLength = polygonsArray.getInt(i * Scene.SIZEOF_POLYGON + 28);
                writer.append(String.format("%4d: ", i));
                writer.append(String.format("index: %4d, ", index));
                writer.append(String.format("label: %4d, ", label));
                writer.append(String.format("circle: [%6d, %2d]", circleStart, circleLength));
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writeSphereIsolated(GL4 gl, int sphereIsolatedCountsBuffer, int sphereIsolatedVSBuffer, int atomCount) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, "sphere-isolated.txt")))) {
            // read counts
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, sphereIsolatedCountsBuffer);
            ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            int counts[] = new int[atomCount];
            data.asIntBuffer().get(counts);
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            // write counts and indices
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, sphereIsolatedVSBuffer);
            ByteBuffer vsData = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < counts.length; i++) {
                writer.append(String.format("%4d (%2d): ", i, counts[i]));
                for (int j = 0; j < counts[i]; j++) {
                    Vector4f vs = getVec4(vsData, (i * Scene.MAX_SPHERE_ISOLATED_TORI + j) * Scene.SIZEOF_VEC4);
                    writer.append(String.format("vs: [%f %f %f %f], ", vs.x, vs.y, vs.z, vs.w));
                }
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writeSphereCapPlanes(GL4 gl, int sphereCapCountsBuffer, int sphereCapPlanesBuffer, int atomCount) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, "sphere-caps.txt")))) {
            // read counts
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, sphereCapCountsBuffer);
            ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            int counts[] = new int[atomCount];
            data.asIntBuffer().get(counts);
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            // write counts and planes
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, sphereCapPlanesBuffer);
            ByteBuffer planesData = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < counts.length; i++) {
                writer.append(String.format("%4d (%2d): ", i, counts[i]));
                for (int j = 0; j < counts[i]; j++) {
                    Vector4f plane = getVec4(planesData, (i * GPUGraph.MAX_SPHERE_POLYGON_COUNT + j) * Scene.SIZEOF_VEC4);
                    writer.append(String.format("plane: [%f %f %f %f], ", plane.x, plane.y, plane.z, plane.w));
                }
                writer.newLine();
            }
            // unbind buffers
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writeCaps(GL4 gl, int capsArrayBuffer, int count) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, "caps.txt")))) {
            // write counts and planes
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, capsArrayBuffer);
            ByteBuffer capsArray = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < count; i++) {
                Vector4f position = getVec4(capsArray, i * Scene.SIZEOF_CAP);
                Vector4f plane = getVec4(capsArray, i * Scene.SIZEOF_CAP + 16);
                int atomIdx = capsArray.getInt(i * Scene.SIZEOF_CAP + 32);
                int label = capsArray.getInt(i * Scene.SIZEOF_CAP + 36);
                int padding0 = capsArray.getInt(i * Scene.SIZEOF_CAP + 40);
                int padding1 = capsArray.getInt(i * Scene.SIZEOF_CAP + 44);
                writer.append(String.format("%4d: ", i));
                writer.append(String.format("position: [%f %f %f %f], ", position.x, position.y, position.z, position.w));
                writer.append(String.format("plane: [%f %f %f %f], ", plane.x, plane.y, plane.z, plane.w));
                writer.append(String.format("atomIdx: %d, label: %d", atomIdx, label));
                writer.append(String.format(", padding0: %d, padding1: %d", padding0, padding1));
                writer.newLine();
            }
            // unbind buffers
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writeDebugi(GL4 gl, int buffer, int count) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, "debug.txt")))) {
            // write debug
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
            IntBuffer debug = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            for (int i = 0; i < count; i++) {
                writer.append(String.format("%4d: %8d", i, debug.get(i)));
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writeDebug4f(GL4 gl, int buffer, int count) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debugDir, "debug.txt")))) {
            // write debug
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
            ByteBuffer debug = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < count; i++) {
                Vector4f v = getVec4(debug, i * 16);
                writer.append(String.format("%4d: [%f %f %f %f]", i, v.x, v.y, v.z, v.w));
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public Vector4f getVec4(ByteBuffer buffer, int index) {
        Vector4f v = new Vector4f();
        v.x = buffer.getFloat(index);
        v.y = buffer.getFloat(index + 4);
        v.z = buffer.getFloat(index + 8);
        v.w = buffer.getFloat(index + 12);
        return v;
    }
    
}
