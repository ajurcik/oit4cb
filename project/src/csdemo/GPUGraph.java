package csdemo;

import com.jogamp.common.nio.Buffers;
import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.GL4;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class GPUGraph {
    
    private int adjacencyProgram;
    private int componentsProgram;
    private int circlesProgram;
    
    private int countersBuffer;
    private int adjacencyBuffer;
    private int verticesBuffer;
    private int labelsBuffer;
    private int circlesBuffer;
    private int circlesCountBuffer;
    private int circlesLengthBuffer;
    private int circlesStartBuffer;
    private int polygonsBuffer;
    
    private int labelsTex;
    private int circlesTex;
    private int circlesCountTex;
    private int circlesLengthTex;
    private int circlesStartTex;
    private int polygonsTex;
    
    private int adjacencyElapsedQuery;
    private int componentsElapsedQuery;
    private int circlesElapsedQuery;
    
    private static final int SIZEOF_ROW = 4 * Buffers.SIZEOF_INT;
    private static final int SIZEOF_VERTEX = 2 * Buffers.SIZEOF_INT;
    private static final int SIZEOF_CIRCLE = 4 * Buffers.SIZEOF_INT;
    
    private static final int MAX_VERTEX_COUNT = Scene.MAX_TRIANGLES;
    public static final int MAX_LABEL_COUNT = 64;
    private static final int MAX_CIRCLE_COUNT = Scene.MAX_TORI;
    private static final int MAX_CIRCLE_EDGE_COUNT = 32;
    private static final int MAX_SPHERE_POLYGON_COUNT = 8;
    
    // buffer indices for shaders
    private static final int EDGES_BUFFER_INDEX = 0;
    private static final int ADJACENCY_BUFFER_INDEX = 1;
    private static final int CIRCLES_BUFFER_INDEX = 2;
    private static final int CIRCLES_LENGTH_BUFFER_INDEX = 3;
    private static final int VERTICES_BUFFER_INDEX = 4;
    private static final int VERTICES_LABEL_BUFFER_INDEX = 5;
    private static final int LABELS_BUFFER_INDEX = 6;
    private static final int CIRCLES_COUNT_BUFFER_INDEX = 0;
    private static final int CIRCLES_START_BUFFER_INDEX = 1;
    private static final int POLYGONS_BUFFER_INDEX = 4;
    private static final int COUNTS_BUFFER_INDEX = 7;
    
    private static final IntBuffer SURFACE_LABEL_DATA = Buffers.newDirectIntBuffer(MAX_LABEL_COUNT + 1);
    private static final int[] surfaceLabels = new int[MAX_LABEL_COUNT + 1];

    private boolean writePerformanceInfo = false;
    
    // Last result
    private Result result;
    
    // Debugging
    private final Debug debug = Debug.getInstance();
    
    public void init(GL4 gl, int sphereCount) {
        // loading resources (shaders, data)
        try {
            adjacencyProgram = Utils.loadComputeProgram(gl, "/resources/shaders/graph/adjacency.glsl");
            componentsProgram = Utils.loadComputeProgram(gl, "/resources/shaders/graph/components.glsl");
            circlesProgram = Utils.loadComputeProgram(gl, "/resources/shaders/graph/circles.glsl");
        } catch (IOException e) {
            System.err.println("Resource loading failed. " + e.getMessage());
            System.exit(1);
        }
        
        int buffers[] = new int[9];
        gl.glGenBuffers(9, buffers, 0);
        countersBuffer = buffers[0];
        adjacencyBuffer = buffers[1];
        verticesBuffer = buffers[2];
        labelsBuffer = buffers[3];
        circlesBuffer = buffers[4];
        circlesCountBuffer = buffers[5];
        circlesLengthBuffer = buffers[6];
        circlesStartBuffer = buffers[7];
        polygonsBuffer = buffers[8];
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, countersBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, adjacencyBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_VERTEX_COUNT * SIZEOF_ROW, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, verticesBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_VERTEX_COUNT * SIZEOF_VERTEX, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, labelsBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, (1 + MAX_LABEL_COUNT) * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, circlesBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_CIRCLE_COUNT * MAX_CIRCLE_EDGE_COUNT * SIZEOF_CIRCLE, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, circlesCountBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, sphereCount * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, circlesLengthBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_CIRCLE_COUNT * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, circlesStartBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_CIRCLE_COUNT * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, polygonsBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, sphereCount * MAX_SPHERE_POLYGON_COUNT * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        // Report memory usage
        int[] value = new int[1];
        int totalSize = 0;
        for (int buffer : buffers) {
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
            gl.glGetBufferParameteriv(GL_SHADER_STORAGE_BUFFER, GL_BUFFER_SIZE, value, 0);
            totalSize += value[0];
        }
        System.out.println(String.format("Memory usage (Graph): %.3f MB", totalSize / 1024.0 / 1024.0));
        
        Utils.bindShaderStorageBlock(gl, adjacencyProgram, "Edges", EDGES_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, adjacencyProgram, "Adjacency", ADJACENCY_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, adjacencyProgram, "Circles", CIRCLES_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, adjacencyProgram, "CirclesLength", CIRCLES_LENGTH_BUFFER_INDEX);
        
        Utils.bindShaderStorageBlock(gl, componentsProgram, "Adjacency", ADJACENCY_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, componentsProgram, "Verts", VERTICES_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, componentsProgram, "VertsLabel", VERTICES_LABEL_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, componentsProgram, "Labels", LABELS_BUFFER_INDEX);
        
        Utils.bindShaderStorageBlock(gl, circlesProgram, "Counts", COUNTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, circlesProgram, "Circles", CIRCLES_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, circlesProgram, "CirclesCount", CIRCLES_COUNT_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, circlesProgram, "CirclesLength", CIRCLES_LENGTH_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, circlesProgram, "CirclesStart", CIRCLES_START_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, circlesProgram, "SpherePolygons", POLYGONS_BUFFER_INDEX);
        
        // textures
        int[] textures = new int[6];
        gl.glGenTextures(5, textures, 1);
        //labelsTex = textures[0];
        circlesTex = textures[1];
        circlesCountTex = textures[2];
        circlesLengthTex = textures[3];
        circlesStartTex = textures[4];
        polygonsTex = textures[5];
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, circlesTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32UI, circlesBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, circlesCountTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, circlesCountBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, circlesLengthTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, circlesLengthBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, circlesStartTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, circlesStartBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, polygonsTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, polygonsBuffer);
        
        /*gl.glBindTexture(GL_TEXTURE_BUFFER, labelsTex);
        gl.glTexBufferRange(GL_TEXTURE_BUFFER, GL_R32UI, labelsBuffer,
                Buffers.SIZEOF_INT, MAX_LABEL_COUNT * Buffers.SIZEOF_INT);*/
        
        // timer query
        int[] queries = new int[3];
        gl.glGenQueries(3, queries, 0);
        
        adjacencyElapsedQuery = queries[0];
        componentsElapsedQuery = queries[1];
        circlesElapsedQuery = queries[2];
    }
    
    public Result connectedComponents(GL4 gl, int toriBuffer, int edgesBuffer, int torusCount,
            int outVerticesBuffer, int vertexCount, int sphereCount) {
        // bind buffers
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, EDGES_BUFFER_INDEX, edgesBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ADJACENCY_BUFFER_INDEX, adjacencyBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, CIRCLES_BUFFER_INDEX, circlesBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, CIRCLES_LENGTH_BUFFER_INDEX, circlesLengthBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, VERTICES_BUFFER_INDEX, verticesBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, VERTICES_LABEL_BUFFER_INDEX, outVerticesBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, LABELS_BUFFER_INDEX, labelsBuffer);
        
        gl.glUseProgram(adjacencyProgram);
        
        // clear adjacency matrix
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, adjacencyBuffer);
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_RGBA32UI, GL_RGBA_INTEGER, GL_UNSIGNED_INT, IntBuffer.wrap(new int[] { 0, 0, 0, 0 }));
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        // clear circles
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, circlesLengthBuffer);
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, IntBuffer.wrap(new int[] { 0 }));
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, circlesStartBuffer);
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, IntBuffer.wrap(new int[] { 0 }));
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        Utils.setUniform(gl, adjacencyProgram, "edgeCount", torusCount);
        
        gl.glBeginQuery(GL_TIME_ELAPSED, adjacencyElapsedQuery);
        gl.glDispatchCompute((torusCount + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        gl.glUseProgram(componentsProgram);
        
        // clear vertices
        final int INFINITY = 0xffffffff;
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, verticesBuffer);
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_RG32UI, GL_RG_INTEGER, GL_UNSIGNED_INT, IntBuffer.wrap(new int[] { INFINITY, 0 }));
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        // clear labels
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, labelsBuffer);
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, IntBuffer.wrap(new int[] { 0 }));
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        Utils.setUniform(gl, componentsProgram, "vertexCount", vertexCount);
        
        gl.glBeginQuery(GL_TIME_ELAPSED, componentsElapsedQuery);
        gl.glDispatchCompute(1, 1, 1);
        gl.glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT | GL_TEXTURE_UPDATE_BARRIER_BIT);
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        // get largest label
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, labelsBuffer);
        gl.glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, MAX_LABEL_COUNT * Buffers.SIZEOF_INT, SURFACE_LABEL_DATA);
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        SURFACE_LABEL_DATA.get(surfaceLabels);
        SURFACE_LABEL_DATA.rewind();
        
        int labelCount = 0;
        while (labelCount < surfaceLabels.length && surfaceLabels[labelCount + 1] > 0) {
            labelCount++;
        }
        
        gl.glUseProgram(circlesProgram);
        
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, COUNTS_BUFFER_INDEX, countersBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, CIRCLES_COUNT_BUFFER_INDEX, circlesCountBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, CIRCLES_START_BUFFER_INDEX, circlesStartBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, POLYGONS_BUFFER_INDEX, polygonsBuffer);
        
        Utils.setCounter(gl, countersBuffer, 0, sphereCount); // totalCircleCount
        Utils.setUniform(gl, circlesProgram, "circleCount", sphereCount);
        
        gl.glBeginQuery(GL_TIME_ELAPSED, circlesElapsedQuery);
        gl.glDispatchCompute((torusCount + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        int circleCount = Utils.getCounter(gl, countersBuffer);
        
        if (writePerformanceInfo) {
            writePerformanceInfo = false;
            // write primitive counts
            System.out.println("New circles: " + (circleCount - sphereCount));
            System.out.println("Surface label: " + surfaceLabels[0]);
            // write timer results
            IntBuffer resultBuffer = Buffers.newDirectIntBuffer(1);
            while (resultBuffer.get(0) != 1) {
                gl.glGetQueryObjectiv(circlesElapsedQuery, GL_QUERY_RESULT_AVAILABLE, resultBuffer);
            }
            // get the query result
            int adjacencyElapsed = Utils.getTimeElapsed(gl.getGL2(), adjacencyElapsedQuery);
            int componentsElapsed = Utils.getTimeElapsed(gl.getGL2(), componentsElapsedQuery);
            int circlesElapsed = Utils.getTimeElapsed(gl.getGL2(), circlesElapsedQuery);
            System.out.println("Time elapsed (adjacency): " + adjacencyElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (components): " + componentsElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (circles): " + circlesElapsed / 1000000.0 + " ms");
            int sgElapsed = adjacencyElapsed + componentsElapsed + circlesElapsed;
            System.out.println("Time elapsed (SG): " + sgElapsed / 1000000.0 + " ms");
        }
        
        result = new Result(vertexCount, labelsTex, surfaceLabels, labelCount,
                circleCount, circlesTex, circlesCountTex, circlesLengthTex, circlesStartTex);
        
        return result;
    }
    
    public void writeResults(GL4 gl) {
        try {
            writeAdjacency(gl, result.getVertexCount());
            writeLabels(gl, result.getVertexCount());
            writeCircles(gl, result.getCircleCount());
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }
    }
    
    public class Result {
        
        // vertices
        private final int vertexCount;
        private final int labelsTex;
        private final int[] surfaceLabels;
        private final int labelCount;
        // circles
        private final int circleCount;
        private final int circlesTex;
        private final int circlesCountTex;
        private final int circlesLengthTex;
        private final int circlesStartTex;

        public Result(int vertexCount, int labelsTex, int[] surfaceLabels, int labelCount,
                int circleCount, int circlesTex, int circlesCountTex, int circlesLengthTex, int circlesStartTex) {
            this.vertexCount = vertexCount;
            this.labelsTex = labelsTex;
            this.surfaceLabels = surfaceLabels;
            this.labelCount = labelCount;
            this.circleCount = circleCount;
            this.circlesTex = circlesTex;
            this.circlesCountTex = circlesCountTex;
            this.circlesLengthTex = circlesLengthTex;
            this.circlesStartTex = circlesStartTex;
        }

        public int getVertexCount() {
            return vertexCount;
        }

        @Deprecated // TODO
        public int getLabelsTex() {
            return labelsTex;
        }
        
        public int[] getSurfaceLabels() {
            return surfaceLabels;
        }
        
        public int getLabelCount() {
            return labelCount;
        }
        
        public int getCircleCount() {
            return circleCount;
        }
        
        public int getCirclesTex() {
            return circlesTex;
        }
        
        public int getCirclesCountTex() {
            return circlesCountTex;
        }

        public int getCirclesLengthTex() {
            return circlesLengthTex;
        }

        public int getCirclesStartTex() {
            return circlesStartTex;
        }
        
        public int getPolygonsTex() {
            return polygonsTex;
        }
        
    }
    
    private void writeAdjacency(GL4 gl, int vertexCount) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debug.getDebugDir(), "adjacency.txt")))) {
            // map adjacency buffer
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, adjacencyBuffer);
            IntBuffer adjacency = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            // write adjacency matrix
            for (int i = 0; i < vertexCount; i++) {
                int v0 = adjacency.get(4 * i + 1);
                int v1 = adjacency.get(4 * i + 2);
                int v2 = adjacency.get(4 * i + 3);
                writer.append(String.format("(%4d): %4d %4d %4d", i, v0, v1, v2));
                writer.newLine();
            }
            // unmap adjacency buffer
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    private void writeLabels(GL4 gl, int vertexCount) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debug.getDebugDir(), "vertices.txt")))) {
            // map labels buffer
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, labelsBuffer);
            IntBuffer labels = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            // write largest label
            writer.append(String.format("largest: %2d", labels.get(0)));
            writer.newLine();
            writer.newLine();
            // write labels
            for (int i = 1; i <= MAX_LABEL_COUNT; i++) {
                int count = labels.get(i);
                writer.append(String.format("(%2d): %4d", i, count));
                writer.newLine();
            }
            // unmap labels buffer
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            
            writer.newLine();
            writer.newLine();
            
            // map vertices buffer
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, verticesBuffer);
            IntBuffer vertices = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            // write adjacency matrix
            for (int i = 0; i < vertexCount; i++) {
                int distance = vertices.get(2 * i);
                int label = vertices.get(2 * i + 1);
                writer.append(String.format("(%4d): %4d %2d", i, distance, label));
                writer.newLine();
            }
            // unmap vertices buffer
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    private void writeCircles(GL4 gl, int circleCount) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(debug.getDebugDir(), "circles.txt")))) {
            // map circles start buffer
            int starts[] = new int[circleCount];
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, circlesStartBuffer);
            IntBuffer startsData = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            startsData.get(starts);
            // unmap circles length buffer
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            
            // map circles length buffer
            int lengths[] = new int[circleCount];
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, circlesLengthBuffer);
            ByteBuffer lengthsData = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            lengthsData.asIntBuffer().get(lengths);
            // unmap circles length buffer
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            
            // map circles buffer
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, circlesBuffer);
            IntBuffer circles = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            // write circles
            for (int i = 0; i < circleCount; i++) {
                writer.append(String.format("(%4d): ", i));
                int offset = 4 * starts[i]; // starts[i] in uvec4 units
                for (int j = 0; j < lengths[i]; j++) {
                    int v0 = circles.get(offset + 4 * j);
                    int v1 = circles.get(offset + 4 * j + 1);
                    writer.append(String.format("(%4d %4d) ", v0, v1));
                }
                writer.newLine();
            }
            // unmap length buffer
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    public void writePerformanceInfo() {
        writePerformanceInfo = true;
    }
    
}
