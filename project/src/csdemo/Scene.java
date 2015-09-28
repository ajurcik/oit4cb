package csdemo;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.util.gl2.GLUT;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import com.jogamp.opengl.DebugGL4bc;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import static com.jogamp.opengl.GL2.*;
import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.GL4bc;
import com.jogamp.opengl.glu.GLU;
import java.awt.Color;
import java.io.File;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class Scene implements GLEventListener {
    
    private final GLU glu = new GLU();
    private final GLUT glut = new GLUT();
    
    private int mode = 2;
    private int polygonModes[] = { GL_POINT, GL_LINE, GL_FILL };
    
    // Contour-buildup programs
    private int hashProgram;
    private int neighborsProgram;
    private int removeProgram;
    private int arcsProgram;
    private int visualizeProgram;
    private int singularityProgram;
    
    private int genSmallCirclesProgram;
    private int readSurfaceAtomsProgram;
    private int writeProgram;
    private int writeSpheresProgram;
    private int smallCirclesProgram;
    private int isolatedProgram;
    
    // ray-casting programs
    private int sphereProgram;
    private int triangleProgram;
    private int torusProgram;
    private int polygonProgram;
    private int kroneTriangleProgram;
    private int kroneTorusProgram;
    private int kronePolygonProgram;
    
    // A-buffer programs
    private int defaultProgram;
    private int resolveProgram;
    
    // Other programs
    private int stickProgram;
    
    private int atomsBuffer;
    private int gridCountsBuffer;
    private int gridIndicesBuffer;
    private int neighborsBuffer;
    private int neighborCountsBuffer;
    private int smallCirclesBuffer;
    private int smallCirclesVisibleBuffer;
    private int arcCountsBuffer;
    private int arcsBuffer;
    private int arcHashesBuffer;
    private int atomsVisibleBuffer;
    private int probeNeighborCountsBuffer;
    private int probeNeighborProbesBuffer;
    private int surfaceEdgesBuffer;
    private int surfaceVerticesBuffer;
    private int surfaceEdgesCircleBuffer;
    private int surfaceEdgesLineBuffer;
    private int isolatedToriBuffer;
    private int polygonsPlanesBuffer;
    private int sphereIsolatedCountsBuffer;
    private int sphereIsolatedVSBuffer;
    private int countersBuffer;
    
    private int atomsTex;
    private int gridCountsTex;
    private int gridIndicesTex;
    private int neighborsTex;
    private int neighborCountsTex;
    private int smallCirclesTex;
    private int smallCirclesVisibleTex;
    private int arcsTex;
    private int arcCountsTex;
    private int arcHashesTex;
    private int probeNeighborCountsTex;
    private int probeNeighborProbesTex;
    private int surfaceEdgesTex;
    private int surfaceVerticesTex;
    private int surfaceEdgesCircleTex;
    private int surfaceEdgesLineTex;
    private int polygonsPlanesTex;
    private int sphereIsolatedCountsTex;
    private int sphereIsolatedVSTex;
    
    // Vertex array buffers
    private int quadArrayBuffer;
    private int smallCirclesArrayBuffer;
    private int spheresArrayBuffer;
    private int trianglesArrayBuffer;
    private int toriArrayBuffer;
    
    // A-buffer buffers
    private int fragmentsBuffer;
    private int fragmentsIndexBuffer;
    
    // Debug buffer
    private int debugBuffer;
    
    // Timer query
    private int hashElapsedQuery;
    private int neighborsElapsedQuery;
    private int removeElapsedQuery;
    private int arcsElapsedQuery;
    private int writeElapsedQuery;
    private int singularityElapsedQuery;
    private int raycastSpheresElapsedQuery;
    private int raycastTrianglesElapsedQuery;
    private int raycastToriElapsedQuery;
    private int resolveElapsedQuery;
    private int miscsElapsedQuery;
    
    private static final int GRID_SIZE = 16;
    private static final int CELL_COUNT = GRID_SIZE * GRID_SIZE * GRID_SIZE;
    private static final int MAX_CELL_ATOMS = 64;
    public static final int MAX_ATOMS = 16384;
    private static final int MAX_PROBES = 32768; // 65536
    private static final int MAX_NEIGHBORS = 128;
    private static final int MAX_ARCS = 32;
    private static final int MAX_SPHERE_ISOLATED_TORI = 8;
    private static final int MAX_TOTAL_ARCS = 32771; // 257, 521, 1031, 2053, 4099, 8209, 16787, 32771, 65537
    private static final int MAX_TOTAL_ARC_HASHES = 196613; // 98317, 196613
    
    private static final int SIZEOF_VEC4 = 4 * Buffers.SIZEOF_FLOAT;
    private static final int SIZEOF_HASH = 4 * Buffers.SIZEOF_INT;
    private static final int SIZEOF_FRAGMENT = 2 * Buffers.SIZEOF_INT + 2 * Buffers.SIZEOF_FLOAT;
    private static final int SIZEOF_TRIANGLE = 4 * SIZEOF_VEC4;
    private static final int SIZEOF_TORUS = 5 * SIZEOF_VEC4;
    private static final int SIZEOF_POLYGON = SIZEOF_VEC4 + 4 * Buffers.SIZEOF_INT;
    private static final int SIZEOF_EDGE = 4 * Buffers.SIZEOF_INT;
    
    public static final int MAX_TRIANGLES = 16384;
    public static final int MAX_TORI = 32768;
    public static final int MAX_ISOLATED_TORI = 256;
    
    private static final int MAX_ABUFFER_WIDTH = 1024;
    private static final int MAX_ABUFFER_HEIGHT = 1024;
    private static final int MAX_ABUFFER_PIXELS = MAX_ABUFFER_WIDTH * MAX_ABUFFER_HEIGHT;
    private static final int MAX_FRAGMENTS = 24;
    private static final int MAX_ABUFFER_FRAGMENTS = MAX_ABUFFER_PIXELS * MAX_FRAGMENTS;
    
    private static final int MAX_HASH_ITERATIONS = 64;
    private static final int INVALID_INDEX = 0xffffffff;
    private static final int INVALID_KEY = 0xffffffff;
    
    private static final int INVALID_LOCATION = -1;
    
    private Point3f aabbMin;
    private float aabbSize;
    private float cellSize;
    
    private Point3f eye = new Point3f(0, 0, 25);
    private Point3f center = new Point3f(0, 0, 24);
    private float totalTilt = 0f;
    private float totalPan = 0f;
    
    private int atomCount;
    private Dynamics dynamics;
    private boolean uploaded = false;
    
    private int snapshot;
    private float t;
    private long lastUpdateTime;
    
    private boolean writeResults = false;
    private boolean writePerformanceInfo = false;
    private boolean displayWholeMolecule = false;
    
    private int atomicCountersBuffer;
    
    // indices for common buffers
    private static final int COUNTERS_BUFFER_INDEX = 6;
    private static final int DEBUG_BUFFER_INDEX = 7;
    // indices for contour-buildup shaders
    private static final int SPHERES_BUFFER_INDEX = 0;
    private static final int GRID_COUNTS_BUFFER_INDEX = 1;
    private static final int GRID_INDICES_BUFFER_INDEX = 2;
    private static final int NEIGHBORS_BUFFER_INDEX = 3;
    private static final int NEIGHBOR_COUNTS_BUFFER_INDEX = 4;
    private static final int SMALL_CIRCLES_BUFFER_INDEX = 5;
    private static final int SMALL_CIRCLES_VISIBLE_BUFFER_INDEX = 6;
    private static final int ARCS_BUFFER_INDEX = 0;
    private static final int ARC_COUNTS_BUFFER_INDEX = 1;
    private static final int ARC_HASHES_BUFFER_INDEX = 2;
    private static final int ARCS_SMALL_CIRCLES_VISIBLE_BUFFER_INDEX = 3;
    // indices for singularity shaders
    private static final int PROBE_NEIGHBOR_COUNTS_BUFFER_INDEX = 3;
    private static final int PROBE_NEIGHBOR_PROBES_BUFFER_INDEX = 4;
    // indices for isolated shader
    private static final int SPHERE_ISOLATED_COUNTS_BUFFER_INDEX = 4;
    private static final int SPHERE_ISOLATED_VS_BUFFER_INDEX = 5;
    // indices for write shaders
    private static final int SPHERES_ARRAY_BUFFER_INDEX = 0;
    private static final int TRIANGLES_ARRAY_BUFFER_INDEX = 0;
    private static final int TORI_ARRAY_BUFFER_INDEX = 1;
    private static final int ISOLATED_TORI_BUFFER_INDEX = 2;
    private static final int POLYGONS_PLANES_BUFFER_INDEX = 3;
    private static final int SURFACE_EDGES_BUFFER_INDEX = 3;
    private static final int SURFACE_EDGES_CIRCLE_BUFFER_INDEX = 4;
    private static final int SURFACE_EDGES_LINE_BUFFER_INDEX = 5;
    // indices for ray-tracing shaders
    private static final int FRAGMENTS_BUFFER_INDEX = 1;
    private static final int FRAGMENTS_INDEX_BUFFER_INDEX = 2;
    private static final int MINMAX_CAVITY_AREA_BUFFER_INDEX = 0; // uniform buffer
    
    // rendering
    private FloatBuffer atomsPos;
    private int sphereCount;
    private int triangleCount;
    private int torusCount;
    private int isolatedTorusCount;
    
    // parameters
    private float probeRadius = 1.4f;
    private Surface surfaceType = Surface.SES;
    private boolean clipSurface = false;
    private float alpha = 0.5f;
    private boolean running = false;
    private float speed = 5.0f;
    private int width;
    private int height;
    private Color sphereColor = Color.RED;
    private Color torusColor = Color.BLUE;
    private Color triangleColor = Color.GREEN;
    private boolean phongLighting = true;
    private boolean ambientOcclusion = true;
    private float aoExponent = 1f;
    private float aoThreshold = 0.7f;
    private boolean silhouettes = true;
    private boolean backfaceModulation = true;
    private Coloring cavityColoring = Coloring.AREA;
    private Color cavityColor1 = Color.YELLOW;
    private Color cavityColor2 = Color.MAGENTA;
    private float threshold = 0f;
    private boolean clipCavities = false;
    private Color tunnelColor = Color.GREEN;
    
    // debug parameters
    private boolean autoupdate = true;
    private boolean update = false;
    private boolean renderSpheres = true;
    private boolean renderTriangles = true;
    private boolean renderTori = true;
    private boolean renderSelectedSphere = false;
    private boolean renderSelectedTriangle = false;
    private boolean renderSelectedTorus = false;
    private boolean renderSelectedCavity = false;
    private int selectedSphere = 0;
    private int selectedTriangle = 0;
    private int selectedTorus = 0;
    private int selectedCavity = 0;
    private boolean updateSurfaceGraph = false;
    private boolean renderPlane = false;
    private boolean renderPoint = false;
    private Vector4f plane = new Vector4f();
    private Vector3f point = new Vector3f();
    private int testTriangleProgram;
    private int testTorusProgram;
    private int testPolygonProgram;

    // spherical polygon
    private final Polygon polygon = new Polygon();
    // GPU graph
    private final GPUGraph gpuGraph = new GPUGraph();
    // Volumetric AO
    private final VolumetricAO volumetricAO = new VolumetricAO();
    // Cavity area estimation
    private final Area area = new Area();
    // Array profiling
    private final GPUPerformance array = new GPUPerformance();
    private final CLPerformance clArray = new CLPerformance();
    private final CLArcs clArcs = new CLArcs();
    private final CLGraph clGraph = new CLGraph();
    
    private Mesh capsule;
    private Drug acetone;
    
    private static final boolean PERFORMANCE_TESTS_ENABLED = false;
    
    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }
    
    public void setProbeRadius(float probeRadius) {
        this.probeRadius = probeRadius;
    }

    public void setSurfaceType(Surface surfaceType) {
        this.surfaceType = surfaceType;
    }

    public void setClipSurface(boolean clipSurface) {
        this.clipSurface = clipSurface;
    }
    
    public void setAutoupdate(boolean autoupdate) {
        this.autoupdate = autoupdate;
    }

    public void setSphereColor(Color sphereColor) {
        this.sphereColor = sphereColor;
    }

    public void setTorusColor(Color torusColor) {
        this.torusColor = torusColor;
    }

    public void setTriangleColor(Color triangleColor) {
        this.triangleColor = triangleColor;
    }

    public void setPhongLighting(boolean phongLighting) {
        this.phongLighting = phongLighting;
    }

    public void setAmbientOcclusion(boolean ambientOcclusion) {
        this.ambientOcclusion = ambientOcclusion;
    }

    public void setAOExponent(float aoExponent) {
        this.aoExponent = aoExponent;
    }

    public void setAOThreshold(float aoThreshold) {
        this.aoThreshold = aoThreshold;
    }

    public void setSilhouettes(boolean silhouettes) {
        this.silhouettes = silhouettes;
    }

    public void setBackfaceModulation(boolean backfaceModulation) {
        this.backfaceModulation = backfaceModulation;
    }

    public void setCavityColoring(Coloring cavityColoring) {
        this.cavityColoring = cavityColoring;
    }

    public void setCavityColor1(Color cavityColor1) {
        this.cavityColor1 = cavityColor1;
    }

    public void setCavityColor2(Color cavityColor2) {
        this.cavityColor2 = cavityColor2;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    public void setTunnelColor(Color tunnelColor) {
        this.tunnelColor = tunnelColor;
    }
    
    public void setSpeed(float speed) {
        this.speed = speed;
    }
    
    public void setRenderSpheres(boolean renderSpheres) {
        this.renderSpheres = renderSpheres;
    }

    public void setRenderTriangles(boolean renderTriangles) {
        this.renderTriangles = renderTriangles;
    }

    public void setRenderTori(boolean renderTori) {
        this.renderTori = renderTori;
    }
    
    public void setRenderPlane(boolean renderPlane) {
        this.renderPlane = renderPlane;
    }

    public void setRenderPoint(boolean renderPoint) {
        this.renderPoint = renderPoint;
    }
    
    public void setRenderSelectedSphere(boolean renderSelectedSphere) {
        this.renderSelectedSphere = renderSelectedSphere;
    }

    public void setRenderSelectedTriangle(boolean renderSelectedTriangle) {
        this.renderSelectedTriangle = renderSelectedTriangle;
    }
    
    public void setRenderSelectedTorus(boolean renderSelectedTorus) {
        this.renderSelectedTorus = renderSelectedTorus;
    }

    public void setRenderSelectedCavity(boolean renderSelectedCavity) {
        this.renderSelectedCavity = renderSelectedCavity;
    }

    public void setSelectedSphere(int selectedSphere) {
        this.selectedSphere = Math.min(selectedSphere, atomCount);
    }
    
    public void setSelectedTriangle(int selectedTriangle) {
        this.selectedTriangle = selectedTriangle;
    }
    
    public void setSelectedTorus(int selectedTorus) {
        this.selectedTorus = selectedTorus;
    }

    public void setSelectedCavity(int selectedCavity) {
        this.selectedCavity = selectedCavity;
    }
    
    public void setClipCavities(boolean clipCavities) {
        this.clipCavities = clipCavities;
    }
    
    public void setPlane(Vector4f plane) {
        this.plane.set(plane);
    }

    public void setPoint(Vector3f point) {
        this.point = point;
    }
    
    public void togglePolygonMode() {
        mode = (++mode) % 3;
    }
    
    public void toggleWholeMolecule() {
        displayWholeMolecule = !displayWholeMolecule;
    }
    
    @Override
    public void init(GLAutoDrawable glad) {
        // get GL2 interface
        GL4bc gl = new DebugGL4bc(glad.getGL().getGL4bc());
        
        // set OpenGL global state
	gl.glEnable(GL_DEPTH_TEST);
        //gl.glEnable(GL_CULL_FACE);
        //gl.glHint(GL_POINT_SMOOTH_HINT, GL_NICEST);
        //gl.glEnable(GL_POINT_SMOOTH);
        gl.glEnable(GL_LIGHTING);
        gl.glEnable(GL_LIGHT0);
        gl.glEnable(GL_COLOR_MATERIAL);
        gl.glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);
        // enable gl_PointSize in shaders
        gl.glEnable(GL_PROGRAM_POINT_SIZE);
        
        // get compute shader resource info
        int maxBindingsCount = getInteger(gl, GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
        int maxBlockSize = getInteger(gl, GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
        int maxComputeBlocksCount = getInteger(gl, GL_MAX_COMPUTE_SHADER_STORAGE_BLOCKS);
        int maxComputeWorkGroupInvocations = getInteger(gl, GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS);
        int maxGeometryOutputComponents = getInteger(gl, GL_MAX_GEOMETRY_OUTPUT_COMPONENTS);
        System.out.println("Max bindings count: " + maxBindingsCount);
        System.out.println("Max SSBO size: " + maxBlockSize);
        System.out.println("Max compute shader SSBO count: " + maxComputeBlocksCount);
        System.out.println("Max compute shader Work Group invocations: " + maxComputeWorkGroupInvocations);
        System.out.println("Max geometry output components: " + maxGeometryOutputComponents);
        
        // get extension info
        boolean available = gl.isExtensionAvailable("GL_ARB_compute_variable_group_size");
        System.out.println("Extension GL_ARB_compute_variable_group_size: " + available);
        available = gl.isExtensionAvailable("GL_INTEL_fragment_shader_ordering");
        System.out.println("Extension GL_INTEL_fragment_shader_ordering: " + available);
        
        // loading resources (shaders, data)
        try {
            // contour-buildup programs
            hashProgram = Utils.loadComputeProgram(gl, "/resources/shaders/cb/hash.glsl");
            neighborsProgram = Utils.loadComputeProgram(gl, "/resources/shaders/cb/neighbors.glsl");
            removeProgram = Utils.loadComputeProgram(gl, "/resources/shaders/cb/remove.glsl");
            arcsProgram = Utils.loadComputeProgram(gl, "/resources/shaders/cb/arcs.glsl");
            writeProgram = Utils.loadComputeProgram(gl, "/resources/shaders/cb/write.glsl");
            writeSpheresProgram = Utils.loadComputeProgram(gl, "/resources/shaders/cb/writeSpheres.glsl");
            singularityProgram = Utils.loadComputeProgram(gl, "/resources/shaders/cb/singularity.glsl");
            isolatedProgram = Utils.loadComputeProgram(gl, "/resources/shaders/cb/isolated.glsl");
            // other programs
            visualizeProgram = Utils.loadProgram(gl, "/resources/shaders/visualize.vert",
                    "/resources/shaders/visualize.frag");
            genSmallCirclesProgram = Utils.loadComputeProgram(gl, "/resources/shaders/genSmallCircles.glsl");
            readSurfaceAtomsProgram = Utils.loadComputeProgram(gl, "/resources/shaders/readSurfaceAtoms.glsl");
            smallCirclesProgram = Utils.loadProgram(gl, "/resources/shaders/smallCircles.vert",
                    "/resources/shaders/smallCircles.frag");
            // ray-casting programs
            sphereProgram = Utils.loadProgram(gl, "/resources/shaders/ray/sphere.vert",
                    "/resources/shaders/ray/sphere.frag");
            triangleProgram = Utils.loadProgram(gl, "/resources/shaders/ray/triangle.vert",
                    "/resources/shaders/ray/triangle.frag");
            torusProgram = Utils.loadProgram(gl, "/resources/shaders/ray/torus.vert",
                    "/resources/shaders/ray/torus.geom", "/resources/shaders/ray/torus.frag");
            polygonProgram = Utils.loadProgram(gl, "/resources/shaders/ray/polygon2.vert",
                    "/resources/shaders/ray/polygon2.geom", "/resources/shaders/ray/polygon2.frag");
            resolveProgram = Utils.loadProgram(gl, "/resources/shaders/resolve.vert",
                    "/resources/shaders/resolve.frag");
            defaultProgram = Utils.loadProgram(gl, "/resources/shaders/default.vert",
                    "/resources/shaders/default.frag");
            stickProgram = Utils.loadProgram(gl, "/resources/shaders/stick.vert",
                    "/resources/shaders/stick.frag");
            kroneTriangleProgram = Utils.loadProgram(gl, "/resources/shaders/ray/triangle.vert",
                    "/resources/shaders/ray/krone/triangle.frag");
            kroneTorusProgram = Utils.loadProgram(gl, "/resources/shaders/ray/torus.vert",
                    "/resources/shaders/ray/torus.geom", "/resources/shaders/ray/krone/torus.frag");
            kronePolygonProgram = Utils.loadProgram(gl, "/resources/shaders/ray/polygon2.vert",
                    "/resources/shaders/ray/polygon2.geom", "/resources/shaders/ray/krone/polygon2.frag");
            // Load molecule
            //dynamics = new Dynamics(Utils.loadDynamicsFromResource("/resources/md/model", 1, 10));
            dynamics = new Dynamics(Collections.singletonList(Utils.loadAtomsFromResource("/resources/1CRN.pdb")));
            System.out.println("Atoms (molecule): " + dynamics.getMolecule().getAtomCount());
            System.out.println("Snapshots: " + dynamics.getSnapshotCount());
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            System.exit(1);
        }
        
        testTriangleProgram = triangleProgram;
        testTorusProgram = torusProgram;
        testPolygonProgram = polygonProgram;
        
        // TODO move to MainWindow
        atomCount = dynamics.getMolecule().getAtomCount();
        
        AABB bb = preprocessAtoms(dynamics.getMolecule());
        updateBoundingBox(bb);
        
        atomsPos = Buffers.newDirectFloatBuffer(MAX_ATOMS * 4);
        
        // Create buffers
        int buffers[] = new int[31];
        gl.glGenBuffers(31, buffers, 0);
        // contour-buildup
        atomsBuffer = buffers[0];
        gridCountsBuffer = buffers[1];
        gridIndicesBuffer = buffers[2];
        neighborsBuffer = buffers[3];
        neighborCountsBuffer = buffers[4];
        smallCirclesBuffer = buffers[5];
        smallCirclesVisibleBuffer = buffers[6];
        arcsBuffer = buffers[7];
        arcCountsBuffer = buffers[8];
        arcHashesBuffer = buffers[9];
        // draw buffers
        smallCirclesArrayBuffer = buffers[10];
        // atomic counters buffer
        atomicCountersBuffer = buffers[11];
        // other draw buffers
        quadArrayBuffer = buffers[12];
        atomsVisibleBuffer = buffers[13];
        spheresArrayBuffer = buffers[14];
        trianglesArrayBuffer = buffers[15];
        toriArrayBuffer = buffers[16];
        // A-buffer buffers
        fragmentsBuffer = buffers[17];
        fragmentsIndexBuffer = buffers[18];
        debugBuffer = buffers[19];
        // singularity handling
        probeNeighborCountsBuffer = buffers[20];
        probeNeighborProbesBuffer = buffers[21];
        // surface graph
        surfaceEdgesBuffer = buffers[22];
        surfaceVerticesBuffer = buffers[23];
        surfaceEdgesCircleBuffer = buffers[24];
        surfaceEdgesLineBuffer = buffers[25];
        // isolated tori handling
        isolatedToriBuffer = buffers[26];
        polygonsPlanesBuffer = buffers[27];
        sphereIsolatedCountsBuffer = buffers[28];
        sphereIsolatedVSBuffer = buffers[29];
        // counters
        countersBuffer = buffers[30];
        
        // quad array buffer
        FloatBuffer quad = FloatBuffer.allocate(16);
        quad.put(new float[] { 1f, -1f, 0f, 1f }, 0, 4);
        quad.put(new float[] { 1f, 1f, 0f, 1f }, 0, 4);
        quad.put(new float[] { -1f, 1f, 0f, 1f }, 0, 4);
        quad.put(new float[] { -1f, -1f, 0f, 1f }, 0, 4);
        quad.rewind();
        gl.glBindBuffer(GL_ARRAY_BUFFER, quadArrayBuffer);
        gl.glBufferData(GL_ARRAY_BUFFER, 4 * SIZEOF_VEC4, quad, GL_STATIC_DRAW);
        
        // 0
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, atomsBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, atomsPos.capacity() * Buffers.SIZEOF_FLOAT, atomsPos, GL_DYNAMIC_DRAW);
        
        // 1
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, gridCountsBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, CELL_COUNT * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        // 2
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, gridIndicesBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, CELL_COUNT * MAX_CELL_ATOMS * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        // 3
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, neighborsBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_ATOMS * MAX_NEIGHBORS * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        // 4
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, neighborCountsBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_ATOMS * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        // 5
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, smallCirclesBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_ATOMS * MAX_NEIGHBORS * SIZEOF_VEC4, null, GL_DYNAMIC_COPY);
        
        // 6
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, smallCirclesVisibleBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_ATOMS * MAX_NEIGHBORS * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        // 0
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcsBuffer);
        //gl.glBufferData(GL_SHADER_STORAGE_BUFFER, atoms.size() * MAX_NEIGHBORS * MAX_ARCS * SIZEOF_VEC4, null, GL_DYNAMIC_COPY);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_TOTAL_ARCS * SIZEOF_VEC4, null, GL_DYNAMIC_COPY);

        // 1
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcCountsBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_ATOMS * MAX_NEIGHBORS * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        // 2
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcHashesBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_TOTAL_ARC_HASHES * SIZEOF_HASH, null, GL_DYNAMIC_COPY);
        
        // 3
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, probeNeighborCountsBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_PROBES * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        // 4
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, probeNeighborProbesBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_PROBES * MAX_NEIGHBORS * SIZEOF_VEC4, null, GL_DYNAMIC_COPY);
        
        // 7
        gl.glBindBuffer(GL_ARRAY_BUFFER, smallCirclesArrayBuffer);
        gl.glBufferData(GL_ARRAY_BUFFER, 32000 * 13 * SIZEOF_VEC4, null, GL_DYNAMIC_COPY);
        
        // 0
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, atomsVisibleBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_ATOMS * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        // 1
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, spheresArrayBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, (3 * MAX_ATOMS / 2) * SIZEOF_POLYGON, null, GL_STREAM_COPY);
        
        // 2
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, trianglesArrayBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_TRIANGLES * SIZEOF_TRIANGLE, null, GL_STREAM_COPY);
        
        // 3
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, toriArrayBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_TORI * SIZEOF_TORUS, null, GL_STREAM_COPY);
        
        // 4
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, surfaceEdgesBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_TORI * SIZEOF_EDGE, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, surfaceVerticesBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_TRIANGLES * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, surfaceEdgesCircleBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_TORI * SIZEOF_VEC4, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, surfaceEdgesLineBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_TORI * SIZEOF_VEC4, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, debugBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, 1024 * Buffers.SIZEOF_INT, null, GL_DYNAMIC_READ);
        
        // 1
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, fragmentsBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_ABUFFER_FRAGMENTS * SIZEOF_FRAGMENT, null, GL_DYNAMIC_COPY);
        
        // 2
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, fragmentsIndexBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, (MAX_ABUFFER_PIXELS + 1) * Buffers.SIZEOF_INT, null, GL_DYNAMIC_READ);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, isolatedToriBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_ISOLATED_TORI * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, polygonsPlanesBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, (3 * MAX_ATOMS / 2) * SIZEOF_VEC4, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, sphereIsolatedCountsBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_ATOMS * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, sphereIsolatedVSBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, MAX_ATOMS * MAX_SPHERE_ISOLATED_TORI * SIZEOF_VEC4, null, GL_DYNAMIC_COPY);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, countersBuffer);
        gl.glBufferData(GL_SHADER_STORAGE_BUFFER, 16 * Buffers.SIZEOF_INT, null, GL_DYNAMIC_READ);
        
        // Report memory usage
        int[] value = new int[1];
        int cbSize = 0;
        int aBufferSize = 0;
        int arcsBufferSize = 0;
        for (int buffer : buffers) {
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
            gl.glGetBufferParameteriv(GL_SHADER_STORAGE_BUFFER, GL_BUFFER_SIZE, value, 0);
            if (buffer == fragmentsBuffer || buffer == fragmentsIndexBuffer) {
                aBufferSize += value[0];
            } else {
                cbSize += value[0];
            }
            if (buffer == arcsBuffer || buffer == arcCountsBuffer || buffer == arcHashesBuffer) {
                arcsBufferSize += value[0];
            }
            //System.out.println(String.format("Memory usage (buffer %d): %.3f MB", buffer, value[0] / 1024.0 / 1024.0));
        }
        System.out.println(String.format("Memory usage (Arcs): %.3f MB", arcsBufferSize / 1024.0 / 1024.0));
        System.out.println(String.format("Memory usage (CB): %.3f MB", cbSize / 1024.0 / 1024.0));
        System.out.println(String.format("Memory usage (A-Buffer): %.3f MB", aBufferSize / 1024.0 / 1024.0));
        
        // Bind buffer indices to programs
        Utils.bindShaderStorageBlock(gl, hashProgram, "Spheres", SPHERES_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, hashProgram, "Counts", GRID_COUNTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, hashProgram, "Indices", GRID_INDICES_BUFFER_INDEX);
        // bind neighbors buffers
        Utils.bindShaderStorageBlock(gl, neighborsProgram, "Neighbors", NEIGHBORS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, neighborsProgram, "NeighborCounts", NEIGHBOR_COUNTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, neighborsProgram, "SmallCircles", SMALL_CIRCLES_BUFFER_INDEX);
        // bind remove covered buffers
        Utils.bindShaderStorageBlock(gl, removeProgram, "NeighborCounts", NEIGHBOR_COUNTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, removeProgram, "SmallCircles", SMALL_CIRCLES_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, removeProgram, "SmallCirclesVisible", SMALL_CIRCLES_VISIBLE_BUFFER_INDEX);
        // bind arcs buffers
        Utils.bindShaderStorageBlock(gl, arcsProgram, "Arcs", ARCS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, arcsProgram, "ArcCounts", ARC_COUNTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, arcsProgram, "ArcHashes", ARC_HASHES_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, arcsProgram, "SmallCirclesVisible", ARCS_SMALL_CIRCLES_VISIBLE_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, arcsProgram, "CountersBuffer", COUNTERS_BUFFER_INDEX);
        // bind singularity buffers
        Utils.bindShaderStorageBlock(gl, singularityProgram, "NeighborCounts", PROBE_NEIGHBOR_COUNTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, singularityProgram, "NeighborProbes", PROBE_NEIGHBOR_PROBES_BUFFER_INDEX);
        // bind read surface atoms buffers
        Utils.bindShaderStorageBlock(gl, readSurfaceAtomsProgram, "NeighborCounts", NEIGHBOR_COUNTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, readSurfaceAtomsProgram, "Neighbors", NEIGHBORS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, readSurfaceAtomsProgram, "ArcsCounts", ARC_COUNTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, readSurfaceAtomsProgram, "Arcs", ARCS_BUFFER_INDEX);
        // bind write primitives buffers
        Utils.bindShaderStorageBlock(gl, writeProgram, "Triangles", TRIANGLES_ARRAY_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, writeProgram, "Tori", TORI_ARRAY_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, writeProgram, "IsolatedTori", ISOLATED_TORI_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, writeProgram, "Edges", SURFACE_EDGES_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, writeProgram, "EdgesCircle", SURFACE_EDGES_CIRCLE_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, writeProgram, "EdgesLine", SURFACE_EDGES_LINE_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, writeProgram, "CountersBuffer", COUNTERS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, writeProgram, "Debug", DEBUG_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, writeSpheresProgram, "Spheres", SPHERES_ARRAY_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, writeSpheresProgram, "CountersBuffer", COUNTERS_BUFFER_INDEX);
        // bind isolated buffers
        Utils.bindShaderStorageBlock(gl, isolatedProgram, "Tori", TORI_ARRAY_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, isolatedProgram, "IsolatedTori", ISOLATED_TORI_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, isolatedProgram, "PolygonsPlanes", POLYGONS_PLANES_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, isolatedProgram, "SphereIsolatedCounts", SPHERE_ISOLATED_COUNTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, isolatedProgram, "SphereIsolatedVisSphere", SPHERE_ISOLATED_VS_BUFFER_INDEX);
        // bind sphere ray-tracing buffers
        Utils.bindShaderStorageBlock(gl, sphereProgram, "ABuffer", FRAGMENTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, sphereProgram, "ABufferIndex", FRAGMENTS_INDEX_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, sphereProgram, "Debug", DEBUG_BUFFER_INDEX);
        // bind triangle ray-tracing buffers
        Utils.bindShaderStorageBlock(gl, triangleProgram, "ABuffer", FRAGMENTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, triangleProgram, "ABufferIndex", FRAGMENTS_INDEX_BUFFER_INDEX);
        Utils.bindUniformBlock(gl, triangleProgram, "MinMaxCavityArea", MINMAX_CAVITY_AREA_BUFFER_INDEX);
        // bind torus ray-tracing buffers
        Utils.bindShaderStorageBlock(gl, torusProgram, "ABuffer", FRAGMENTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, torusProgram, "ABufferIndex", FRAGMENTS_INDEX_BUFFER_INDEX);
        Utils.bindUniformBlock(gl, torusProgram, "MinMaxCavityArea", MINMAX_CAVITY_AREA_BUFFER_INDEX);
        // bind polygon ray-tracing buffers
        Utils.bindShaderStorageBlock(gl, polygonProgram, "ABuffer", FRAGMENTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, polygonProgram, "ABufferIndex", FRAGMENTS_INDEX_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, polygonProgram, "Debug", DEBUG_BUFFER_INDEX);
        Utils.bindUniformBlock(gl, polygonProgram, "MinMaxCavityArea", MINMAX_CAVITY_AREA_BUFFER_INDEX);
        // bind A-buffer buffers
        Utils.bindShaderStorageBlock(gl, defaultProgram, "ABuffer", FRAGMENTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, defaultProgram, "ABufferIndex", FRAGMENTS_INDEX_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, stickProgram, "ABuffer", FRAGMENTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, stickProgram, "ABufferIndex", FRAGMENTS_INDEX_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, resolveProgram, "ABuffer", FRAGMENTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, resolveProgram, "ABufferIndex", FRAGMENTS_INDEX_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, resolveProgram, "CountersBuffer", COUNTERS_BUFFER_INDEX);
        //Utils.bindShaderStorageBlock(gl, resolveProgram, "Debug", DEBUG_BUFFER_INDEX);
        
        // triangle test program
        Utils.bindShaderStorageBlock(gl, kroneTriangleProgram, "ABuffer", FRAGMENTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, kroneTriangleProgram, "ABufferIndex", FRAGMENTS_INDEX_BUFFER_INDEX);
        Utils.bindUniformBlock(gl, kroneTriangleProgram, "MinMaxCavityArea", MINMAX_CAVITY_AREA_BUFFER_INDEX);
        // torus test program
        Utils.bindShaderStorageBlock(gl, kroneTorusProgram, "ABuffer", FRAGMENTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, kroneTorusProgram, "ABufferIndex", FRAGMENTS_INDEX_BUFFER_INDEX);
        Utils.bindUniformBlock(gl, kroneTorusProgram, "MinMaxCavityArea", MINMAX_CAVITY_AREA_BUFFER_INDEX);
        // polygon test program
        Utils.bindShaderStorageBlock(gl, kronePolygonProgram, "ABuffer", FRAGMENTS_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, kronePolygonProgram, "ABufferIndex", FRAGMENTS_INDEX_BUFFER_INDEX);
        Utils.bindShaderStorageBlock(gl, kronePolygonProgram, "Debug", DEBUG_BUFFER_INDEX);
        Utils.bindUniformBlock(gl, kronePolygonProgram, "MinMaxCavityArea", MINMAX_CAVITY_AREA_BUFFER_INDEX);
        
        // textures
        int[] textures = new int[19];
        gl.glGenTextures(19, textures, 0);
        atomsTex = textures[0];
        gridCountsTex = textures[1];
        gridIndicesTex = textures[2];
        neighborsTex = textures[3];
        neighborCountsTex = textures[4];
        smallCirclesTex = textures[5];
        smallCirclesVisibleTex = textures[6];
        arcsTex = textures[7];
        arcCountsTex = textures[8];
        arcHashesTex = textures[9];
        probeNeighborCountsTex = textures[10];
        probeNeighborProbesTex = textures[11];
        surfaceEdgesTex = textures[12];
        surfaceVerticesTex = textures[13];
        surfaceEdgesCircleTex = textures[14];
        surfaceEdgesLineTex = textures[15];
        polygonsPlanesTex = textures[16];
        sphereIsolatedCountsTex = textures[17];
        sphereIsolatedVSTex = textures[18];
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, atomsTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, atomsBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, gridCountsTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, gridCountsBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, gridIndicesTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, gridIndicesBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, neighborsTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, neighborsBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, neighborCountsTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, neighborCountsBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, smallCirclesTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, smallCirclesBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, smallCirclesVisibleTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, smallCirclesVisibleBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, arcsTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, arcsBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, arcCountsTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, arcCountsBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, arcHashesTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32UI, arcHashesBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, probeNeighborCountsTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, probeNeighborCountsBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, probeNeighborProbesTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, probeNeighborProbesBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, surfaceEdgesTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32UI, surfaceEdgesBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, surfaceVerticesTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, surfaceVerticesBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, surfaceEdgesCircleTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, surfaceEdgesCircleBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, surfaceEdgesLineTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, surfaceEdgesLineBuffer);
        
        gl.glBindTexture(GL_TEXTURE_BUFFER, polygonsPlanesTex);
        gl.glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, polygonsPlanesBuffer);
        
        Utils.bindTextureBuffer(gl, sphereIsolatedCountsTex, GL_R32UI, sphereIsolatedCountsBuffer);
        Utils.bindTextureBuffer(gl, sphereIsolatedVSTex, GL_RGBA32F, sphereIsolatedVSBuffer);
        
        int atomicCounterBufferIndex = 0;
        gl.glBindBuffer(GL_ATOMIC_COUNTER_BUFFER, atomicCountersBuffer);
        gl.glBufferData(GL_ATOMIC_COUNTER_BUFFER, 4 * Buffers.SIZEOF_INT, null, GL_DYNAMIC_COPY);
        gl.glBindBuffer(GL_ATOMIC_COUNTER_BUFFER, 0);
        gl.glBindBufferBase(GL_ATOMIC_COUNTER_BUFFER, atomicCounterBufferIndex, atomicCountersBuffer);
        
        // timer query
        int[] queries = new int[11];
        gl.glGenQueries(11, queries, 0);
        
        hashElapsedQuery = queries[0];
        neighborsElapsedQuery = queries[1];
        removeElapsedQuery = queries[2];
        arcsElapsedQuery = queries[3];
        writeElapsedQuery = queries[4];
        singularityElapsedQuery = queries[5];
        raycastSpheresElapsedQuery = queries[6];
        raycastTrianglesElapsedQuery = queries[7];
        raycastToriElapsedQuery = queries[8];
        resolveElapsedQuery = queries[9];
        miscsElapsedQuery = queries[10];
        
        // init spherical polygon
        polygon.init(gl, FRAGMENTS_BUFFER_INDEX, FRAGMENTS_INDEX_BUFFER_INDEX);
        // init GPU graph
        gpuGraph.init(gl, MAX_ATOMS);
        // init volumetric AO
        volumetricAO.init(gl);
        // init area estimation
        area.init(gl);
        
        // init OpenCL for arcs computation (same speed as CUDA on nVidia)
        clArcs.init(gl, atomsBuffer, neighborsBuffer, neighborCountsBuffer, smallCirclesBuffer,
                arcsBuffer, arcCountsBuffer, arcHashesBuffer, smallCirclesVisibleBuffer);
        
        //clGraph.init(gl);
        
        // init GPU array
        if (PERFORMANCE_TESTS_ENABLED) {
            array.init(gl);
            clArray.init(gl);
        }
        
        updateAtomPositions(gl);
        volumetricAO.updateVolumes(gl, dynamics.getMolecule().getAtoms());
        uploaded = true;
        
        capsule = Utils.loadMesh(gl, "/resources/obj/capsule.obj");
        
        // DEBUG acetone molecule
        try {
            acetone = Utils.loadAcetone();
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }
    }

    @Override
    public void dispose(GLAutoDrawable glad) {
    }

    @Override
    public void display(GLAutoDrawable glad) {
        //System.out.println("Begin display(...)");
        // Get GL2 interface
        GL4bc gl = new DebugGL4bc(glad.getGL().getGL4bc());
        
        if (!uploaded) {
            updateAtomPositions(gl);
            volumetricAO.updateVolumes(gl, dynamics.getMolecule().getAtoms());
            uploaded = true;
        }
        
        if (running) {
            updateAtomPositions(gl);
        }
        
        // Clear buffers
        gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        
        gl.glPolygonMode(GL_FRONT_AND_BACK, polygonModes[mode]);
        
        // Set look at matrix
	gl.glLoadIdentity();
	glu.gluLookAt(eye.x, eye.y, eye.z,
		center.x, center.y, center.z,
		0, 1, 0);
        
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SPHERES_BUFFER_INDEX, atomsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, GRID_COUNTS_BUFFER_INDEX, gridCountsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, GRID_INDICES_BUFFER_INDEX, gridIndicesBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NEIGHBORS_BUFFER_INDEX, neighborsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NEIGHBOR_COUNTS_BUFFER_INDEX, neighborCountsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SMALL_CIRCLES_BUFFER_INDEX, smallCirclesBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SMALL_CIRCLES_VISIBLE_BUFFER_INDEX, smallCirclesVisibleBuffer);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, gridCountsBuffer);
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, IntBuffer.wrap(new int[] { 0 }));
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        gl.glUseProgram(hashProgram);
        Utils.setUniform(gl, hashProgram, "sphereCount", atomCount);
        Utils.setUniform(gl, hashProgram, "maxNumSpheres", MAX_CELL_ATOMS);
        Utils.setUniform(gl, hashProgram, "gridSize", GRID_SIZE);
        Utils.setUniform(gl, hashProgram, "cellSize", cellSize);
        gl.glBeginQuery(GL_TIME_ELAPSED, hashElapsedQuery);
        gl.glDispatchCompute((atomCount + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        gl.glEndQuery(GL_TIME_ELAPSED);gl.glFinish();
        
        gl.glUseProgram(neighborsProgram);
        
        gl.glActiveTexture(GL_TEXTURE0);
        gl.glBindTexture(GL_TEXTURE_BUFFER, atomsTex);
        gl.glActiveTexture(GL_TEXTURE1);
        gl.glBindTexture(GL_TEXTURE_BUFFER, gridCountsTex);
        gl.glActiveTexture(GL_TEXTURE2);
        gl.glBindTexture(GL_TEXTURE_BUFFER, gridIndicesTex);
        
        Utils.setSampler(gl, neighborsProgram, "spheresTex", 0);
        Utils.setSampler(gl, neighborsProgram, "gridCountsTex", 1);
        Utils.setSampler(gl, neighborsProgram, "gridIndicesTex", 2);
        
        Utils.setUniform(gl, neighborsProgram, "gridSize", GRID_SIZE);
        Utils.setUniform(gl, neighborsProgram, "cellSize", cellSize);
        Utils.setUniform(gl, neighborsProgram, "sphereCount", atomCount);
        Utils.setUniform(gl, neighborsProgram, "maxNumSpheres", MAX_CELL_ATOMS);
        Utils.setUniform(gl, neighborsProgram, "maxNumNeighbors", MAX_NEIGHBORS);
        Utils.setUniform(gl, neighborsProgram, "probeRadius", probeRadius);
        gl.glBeginQuery(GL_TIME_ELAPSED, neighborsElapsedQuery);
        gl.glDispatchCompute((atomCount + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        gl.glEndQuery(GL_TIME_ELAPSED);gl.glFinish();
        
        gl.glUseProgram(removeProgram);
        
        gl.glActiveTexture(GL_TEXTURE0);
        gl.glBindTexture(GL_TEXTURE_BUFFER, atomsTex);
        gl.glActiveTexture(GL_TEXTURE1);
        gl.glBindTexture(GL_TEXTURE_BUFFER, neighborsTex);
        gl.glActiveTexture(GL_TEXTURE2);
        gl.glBindTexture(GL_TEXTURE_BUFFER, neighborCountsTex);
        gl.glActiveTexture(GL_TEXTURE3);
        gl.glBindTexture(GL_TEXTURE_BUFFER, smallCirclesTex);
        
        Utils.setSampler(gl, removeProgram, "atomsTex", 0);
        Utils.setSampler(gl, removeProgram, "neighborsTex", 1);
        Utils.setSampler(gl, removeProgram, "neighborCountsTex", 2);
        Utils.setSampler(gl, removeProgram, "smallCirclesTex", 3);
        
        Utils.setUniform(gl, removeProgram, "atomsCount", atomCount);
        Utils.setUniform(gl, removeProgram, "maxNumNeighbors", MAX_NEIGHBORS);
        Utils.setUniform(gl, removeProgram, "probeRadius", probeRadius);
        gl.glBeginQuery(GL_TIME_ELAPSED, removeElapsedQuery);
        gl.glDispatchCompute((MAX_NEIGHBORS + 63) / 64, (atomCount + 1) / 2, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        gl.glEndQuery(GL_TIME_ELAPSED);gl.glFinish();
        
        gl.glUseProgram(arcsProgram);
        
        Utils.clearCounter(gl, countersBuffer, 0); // DEBUG threadCount
        Utils.clearCounter(gl, countersBuffer, 4); // DEBUG hashCount
        Utils.clearCounter(gl, countersBuffer, 8); // hashErrorCount
        Utils.clearCounter(gl, countersBuffer, 12); // arcCount
        
        gl.glActiveTexture(GL_TEXTURE0);
        gl.glBindTexture(GL_TEXTURE_BUFFER, atomsTex);
        gl.glActiveTexture(GL_TEXTURE1);
        gl.glBindTexture(GL_TEXTURE_BUFFER, neighborsTex);
        gl.glActiveTexture(GL_TEXTURE2);
        gl.glBindTexture(GL_TEXTURE_BUFFER, neighborCountsTex);
        gl.glActiveTexture(GL_TEXTURE3);
        gl.glBindTexture(GL_TEXTURE_BUFFER, smallCirclesTex);
        
        if (autoupdate || update) {
            // DEBUG
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcsBuffer);
            gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_RGBA32F, GL_RGBA, GL_FLOAT, FloatBuffer.wrap(new float[] { 1f, 2f, 3f, 4f }));
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcCountsBuffer);
            gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, IntBuffer.wrap(new int[] { 0 }));
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcHashesBuffer);
            //gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_RGB32UI, GL_RGB_INTEGER, GL_UNSIGNED_INT, IntBuffer.wrap(new int[] { INVALID_KEY, 0, 0 }));
            gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, IntBuffer.wrap(new int[] { INVALID_KEY, 0, 0 })); // ATI GL_RGB32UI bug
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }
        
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ARCS_BUFFER_INDEX, arcsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ARC_COUNTS_BUFFER_INDEX, arcCountsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ARC_HASHES_BUFFER_INDEX, arcHashesBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ARCS_SMALL_CIRCLES_VISIBLE_BUFFER_INDEX, smallCirclesVisibleBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, COUNTERS_BUFFER_INDEX, countersBuffer);
        
        Utils.setSampler(gl, arcsProgram, "atomsTex", 0);
        Utils.setSampler(gl, arcsProgram, "neighborsTex", 1);
        Utils.setSampler(gl, arcsProgram, "neighborCountsTex", 2);
        Utils.setSampler(gl, arcsProgram, "smallCirclesTex", 3);
        
        Utils.setUniform(gl, arcsProgram, "atomsCount", atomCount);
        Utils.setUniform(gl, arcsProgram, "probeRadius", probeRadius);
        Utils.setUniform(gl, arcsProgram, "maxNumNeighbors", MAX_NEIGHBORS);
        //Utils.setUniform(gl, arcsProgram, "maxNumArcs", MAX_ARCS);
        Utils.setUniform(gl, arcsProgram, "maxNumTotalArcHashes", MAX_TOTAL_ARC_HASHES);
        Utils.setUniform(gl, arcsProgram, "maxHashIterations", MAX_HASH_ITERATIONS);
        gl.glBeginQuery(GL_TIME_ELAPSED, arcsElapsedQuery);
        if (autoupdate || update) {
            //gl.glDispatchCompute((MAX_NEIGHBORS + 31) / 32, (atoms.size() + 7) / 8, 1); // arcs program
            //gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
            //triangleCount = Utils.getCounter(gl, countersBuffer, 12);
            //System.out.println("Before CLArcs");
            triangleCount = clArcs.computeArcs(gl, atomCount, MAX_NEIGHBORS, MAX_TOTAL_ARC_HASHES, MAX_HASH_ITERATIONS, probeRadius);
            //System.out.println("After CLArcs");
        }
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        //int threadCount = Utils.getCounter(gl, countersBuffer, 0);
        //System.out.println("Arcs threads finished: " + threadCount);
        //int hashCount = Utils.getCounter(gl, countersBuffer, 4);
        //System.out.println("Arc hashes written: " + hashCount);
        int hashErrorCount = Utils.getCounter(gl, countersBuffer, 8);
        if (hashErrorCount > 0) {
            System.out.println("Arc hash errors: " + hashErrorCount);
        }
        
        if (writeResults) {
            try {
                writeGrid(gl, "grid.txt");
                writeNeighbors(gl, neighborsBuffer, neighborCountsBuffer, atomCount, "neighbors.txt");
                writeArcs(gl);
                writeArcHashes(gl, MAX_TOTAL_ARC_HASHES);
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
        
        gl.glBeginQuery(GL_TIME_ELAPSED, writeElapsedQuery);
        
        // write triangles and tori primitives
        gl.glUseProgram(writeProgram);
        
        Utils.clearCounter(gl, countersBuffer, 0); // toriCount
        Utils.clearCounter(gl, countersBuffer, 4); // isolatedToriCount
        Utils.clearCounter(gl, countersBuffer, 8); // hashErrorCount
        
        gl.glActiveTexture(GL_TEXTURE0);
        gl.glBindTexture(GL_TEXTURE_BUFFER, atomsTex);
        gl.glActiveTexture(GL_TEXTURE1);
        gl.glBindTexture(GL_TEXTURE_BUFFER, neighborsTex);
        gl.glActiveTexture(GL_TEXTURE2);
        gl.glBindTexture(GL_TEXTURE_BUFFER, neighborCountsTex);
        gl.glActiveTexture(GL_TEXTURE3);
        gl.glBindTexture(GL_TEXTURE_BUFFER, smallCirclesTex);
        gl.glActiveTexture(GL_TEXTURE4);
        gl.glBindTexture(GL_TEXTURE_BUFFER, smallCirclesVisibleTex);
        gl.glActiveTexture(GL_TEXTURE5);
        gl.glBindTexture(GL_TEXTURE_BUFFER, arcsTex);
        gl.glActiveTexture(GL_TEXTURE6);
        gl.glBindTexture(GL_TEXTURE_BUFFER, arcCountsTex);
        gl.glActiveTexture(GL_TEXTURE7);
        gl.glBindTexture(GL_TEXTURE_BUFFER, arcHashesTex);

        if (autoupdate || update) {
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, surfaceEdgesBuffer);
            gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_RGBA32UI, GL_RGBA_INTEGER, GL_UNSIGNED_INT,
                    IntBuffer.wrap(new int[] { INVALID_INDEX, INVALID_INDEX, 0, 0 }));
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }
        
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TRIANGLES_ARRAY_BUFFER_INDEX, trianglesArrayBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TORI_ARRAY_BUFFER_INDEX, toriArrayBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ISOLATED_TORI_BUFFER_INDEX, isolatedToriBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SURFACE_EDGES_BUFFER_INDEX, surfaceEdgesBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SURFACE_EDGES_CIRCLE_BUFFER_INDEX, surfaceEdgesCircleBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SURFACE_EDGES_LINE_BUFFER_INDEX, surfaceEdgesLineBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, COUNTERS_BUFFER_INDEX, countersBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, DEBUG_BUFFER_INDEX, debugBuffer);

        Utils.setSampler(gl, writeProgram, "atomsTex", 0);
        Utils.setSampler(gl, writeProgram, "neighborsTex", 1);
        Utils.setSampler(gl, writeProgram, "neighborCountsTex", 2);
        Utils.setSampler(gl, writeProgram, "smallCirclesTex", 3);
        Utils.setSampler(gl, writeProgram, "smallCircleVisibleTex", 4);
        Utils.setSampler(gl, writeProgram, "arcsTex", 5);
        Utils.setSampler(gl, writeProgram, "arcCountsTex", 6);
        Utils.setSampler(gl, writeProgram, "arcHashesTex", 7);
        
        Utils.setUniform(gl, writeProgram, "atomsCount", atomCount);
        Utils.setUniform(gl, writeProgram, "maxNumNeighbors", MAX_NEIGHBORS);
        //setUniform(gl, writeProgram, "maxNumArcs", MAX_ARCS);
        Utils.setUniform(gl, writeProgram, "maxNumTotalArcHashes", MAX_TOTAL_ARC_HASHES);
        Utils.setUniform(gl, writeProgram, "maxHashIterations", MAX_HASH_ITERATIONS);
        
        if (autoupdate || update) {
            gl.glDispatchCompute((atomCount + 63) / 64, 1, 1); // writeProgram
            gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
            torusCount = Utils.getCounter(gl, countersBuffer, 0);
            isolatedTorusCount = Utils.getCounter(gl, countersBuffer, 4);
            int readHashErrorCount = Utils.getCounter(gl, countersBuffer, 8);
            if (readHashErrorCount > 0) {
                System.err.println("Warning: Read hash errors: " + readHashErrorCount);
            }
        }
        
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        // compute surface graph
        GPUGraph.Result gr = null;
        if (gr == null || autoupdate || updateSurfaceGraph) {
            updateSurfaceGraph = false;
            /*Graph graph = new Graph();
            graph.connectedComponents(gl, toriArrayBuffer, surfaceEdgesBuffer, torusCount,
                    surfaceVerticesBuffer, triangleCount, atoms.size());*/
            // Compute graph on GPU
            gr = gpuGraph.connectedComponents(gl, toriArrayBuffer, surfaceEdgesBuffer, torusCount,
                    surfaceVerticesBuffer, triangleCount, atomCount);
        }
        
        // compute ambient occlusion
        int aoVolumeTex = -1;
        if (aoVolumeTex == -1 || autoupdate) {
            aoVolumeTex = volumetricAO.ambientOcclusion(gl, atomsBuffer, atomCount, aabbSize);
        }
        
        // compute cavity area
        Area.Result ar = null;
        if (ar == null || autoupdate) {
            ar = area.computeArea(gl, trianglesArrayBuffer, triangleCount, surfaceVerticesTex, gr.getLabelCount());
        }
        
        gl.glBeginQuery(GL_TIME_ELAPSED, miscsElapsedQuery);
        
        // handle surface of isolated tori
        gl.glUseProgram(isolatedProgram);
        
        if (autoupdate || update) {
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, polygonsPlanesBuffer);
            gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_RGBA32F, GL_RGBA, GL_FLOAT,
                    FloatBuffer.wrap(new float[] { 0f, 0f, 0f, 0f }));
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
            
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, sphereIsolatedCountsBuffer);
            gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT,
                    IntBuffer.wrap(new int[] { 0 }));
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
            
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, sphereIsolatedVSBuffer);
            gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_RGBA32F, GL_RGBA, GL_FLOAT,
                    FloatBuffer.wrap(new float[] { 0f, 0f, 0f, 0f }));
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }
        
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TORI_ARRAY_BUFFER_INDEX, toriArrayBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ISOLATED_TORI_BUFFER_INDEX, isolatedToriBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, POLYGONS_PLANES_BUFFER_INDEX, polygonsPlanesBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SPHERE_ISOLATED_COUNTS_BUFFER_INDEX, sphereIsolatedCountsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SPHERE_ISOLATED_VS_BUFFER_INDEX, sphereIsolatedVSBuffer);
        
        gl.glActiveTexture(GL_TEXTURE1);
        gl.glBindTexture(GL_TEXTURE_BUFFER, gr.getCirclesTex());
        gl.glActiveTexture(GL_TEXTURE2);
        gl.glBindTexture(GL_TEXTURE_BUFFER, gr.getCirclesCountTex());
        gl.glActiveTexture(GL_TEXTURE3);
        gl.glBindTexture(GL_TEXTURE_BUFFER, surfaceVerticesTex);
        gl.glActiveTexture(GL_TEXTURE4);
        gl.glBindTexture(GL_TEXTURE_BUFFER, gr.getPolygonsTex());
        
        Utils.setSampler(gl, isolatedProgram, "circlesTex", 1);
        Utils.setSampler(gl, isolatedProgram, "circlesCountTex", 2);
        Utils.setSampler(gl, isolatedProgram, "labelsTex", 3);
        //Utils.setSampler(gl, isolatedProgram, "polygonsTex", 4);
        
        Utils.setUniform(gl, isolatedProgram, "torusCount", isolatedTorusCount);
        Utils.setUniform(gl, isolatedProgram, "maxSphereIsolatedTori", MAX_SPHERE_ISOLATED_TORI);
        
        if (autoupdate || update) {
            gl.glDispatchCompute((isolatedTorusCount + 63) / 64, 1, 1); // isolatedProgram
            gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        }
        
        // write spheres primitives
        gl.glUseProgram(writeSpheresProgram);
        
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SPHERES_ARRAY_BUFFER_INDEX, spheresArrayBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, COUNTERS_BUFFER_INDEX, countersBuffer);
        //gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, DEBUG_BUFFER_INDEX, debugBuffer);
        
        Utils.clearCounter(gl, countersBuffer, 0);
        
        gl.glActiveTexture(GL_TEXTURE0);
        gl.glBindTexture(GL_TEXTURE_BUFFER, atomsTex);
        gl.glActiveTexture(GL_TEXTURE1);
        gl.glBindTexture(GL_TEXTURE_BUFFER, gr.getCirclesTex());
        gl.glActiveTexture(GL_TEXTURE2);
        gl.glBindTexture(GL_TEXTURE_BUFFER, gr.getCirclesLengthTex());
        gl.glActiveTexture(GL_TEXTURE3);
        gl.glBindTexture(GL_TEXTURE_BUFFER, gr.getCirclesStartTex());
        gl.glActiveTexture(GL_TEXTURE4);
        gl.glBindTexture(GL_TEXTURE_BUFFER, surfaceVerticesTex);
        //gl.glActiveTexture(GL_TEXTURE5);
        //gl.glBindTexture(GL_TEXTURE_BUFFER, polygonsPlanesTex);
        
        Utils.setSampler(gl, writeSpheresProgram, "atomsTex", 0);
        Utils.setSampler(gl, writeSpheresProgram, "circlesTex", 1);
        Utils.setSampler(gl, writeSpheresProgram, "circlesLengthTex", 2);
        Utils.setSampler(gl, writeSpheresProgram, "circlesStartTex", 3);
        Utils.setSampler(gl, writeSpheresProgram, "labelsTex", 4);
        //Utils.setSampler(gl, writeSpheresProgram, "polygonsPlanesTex", 5);
        
        //Utils.setUniform(gl, writeSpheresProgram, "atomCount", atoms.size());
        Utils.setUniform(gl, writeSpheresProgram, "circleCount", gr.getCircleCount());
        
        if (autoupdate || update) {
            gl.glDispatchCompute((gr.getCircleCount() + 63) / 64, 1, 1); // writeSpheresProgram
            gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
            sphereCount = Utils.getCounter(gl, countersBuffer, 0);
        }
        
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        gl.glBeginQuery(GL_TIME_ELAPSED, singularityElapsedQuery);
        
        // singularity handling
        gl.glUseProgram(hashProgram);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, gridCountsBuffer);
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, IntBuffer.wrap(new int[] { 0 }));
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SPHERES_BUFFER_INDEX, arcsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, GRID_COUNTS_BUFFER_INDEX, gridCountsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, GRID_INDICES_BUFFER_INDEX, gridIndicesBuffer);
        //gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, PROBE_NEIGHBORS_BUFFER_INDEX, probeNeighborsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, PROBE_NEIGHBOR_COUNTS_BUFFER_INDEX, probeNeighborCountsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, PROBE_NEIGHBOR_PROBES_BUFFER_INDEX, probeNeighborProbesBuffer);
        
        Utils.setUniform(gl, hashProgram, "sphereCount", triangleCount);
        Utils.setUniform(gl, hashProgram, "maxNumSpheres", MAX_CELL_ATOMS);
        Utils.setUniform(gl, hashProgram, "gridSize", GRID_SIZE);
        Utils.setUniform(gl, hashProgram, "cellSize", cellSize);
        gl.glDispatchCompute((triangleCount + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        
        gl.glUseProgram(singularityProgram);
        
        gl.glActiveTexture(GL_TEXTURE0);
        gl.glBindTexture(GL_TEXTURE_BUFFER, arcsTex);
        gl.glActiveTexture(GL_TEXTURE1);
        gl.glBindTexture(GL_TEXTURE_BUFFER, gridCountsTex);
        gl.glActiveTexture(GL_TEXTURE2);
        gl.glBindTexture(GL_TEXTURE_BUFFER, gridIndicesTex);
        
        Utils.setSampler(gl, singularityProgram, "probesTex", 0);
        Utils.setSampler(gl, singularityProgram, "gridCountsTex", 1);
        Utils.setSampler(gl, singularityProgram, "gridIndicesTex", 2);
        
        Utils.setUniform(gl, singularityProgram, "gridSize", GRID_SIZE);
        Utils.setUniform(gl, singularityProgram, "cellSize", cellSize);
        Utils.setUniform(gl, singularityProgram, "probeCount", triangleCount);
        Utils.setUniform(gl, singularityProgram, "maxNumSpheres", MAX_CELL_ATOMS);
        Utils.setUniform(gl, singularityProgram, "maxNumNeighbors", MAX_NEIGHBORS);
        Utils.setUniform(gl, singularityProgram, "probeRadius", probeRadius);
        
        if (autoupdate || update) {
            update = false;
            gl.glDispatchCompute((triangleCount + 63) / 64, 1, 1); // singularity program
            gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        }
        
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        gl.glBeginQuery(GL_TIME_ELAPSED, raycastSpheresElapsedQuery);
        
        // bind and clear A-Buffer
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, fragmentsIndexBuffer);
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT,
                IntBuffer.wrap(new int[] { INVALID_INDEX }));
        gl.glClearBufferSubData(GL_SHADER_STORAGE_BUFFER, GL_R32UI, 0, Buffers.SIZEOF_INT,
                GL_RED_INTEGER, GL_UNSIGNED_INT, IntBuffer.wrap(new int[] { 0 }));
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, FRAGMENTS_BUFFER_INDEX, fragmentsBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, FRAGMENTS_INDEX_BUFFER_INDEX, fragmentsIndexBuffer);
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, COUNTERS_BUFFER_INDEX, countersBuffer);
        
        gl.glUseProgram(defaultProgram);
        //Utils.drawAxes(gl, 5.0f);
        
        gl.glPushMatrix();
        gl.glTranslatef(aabbMin.x - 4f, aabbMin.y - 4f, aabbMin.z - 4f);
        
        // DEBUG draw small circles
        //int smallCirclesCount = getAtomicCounter(gl, atomicCounterBuffer);
        //drawSmallCircles(gl, smallCirclesCount);
        
        // DEBUG torus planes
        if (renderPlane) {
            Utils.drawPlane(gl, plane, 64f);
        }
        
        if (renderPoint) {
            //Utils.drawPoint(gl, point, 2f);
        }
        
        // calc view vectors
        Vector3f view = new Vector3f();
        Vector3f up = new Vector3f(0f, 1f, 0f);
        Vector3f right = new Vector3f();
        view.sub(center, eye);
        view.normalize();
        right.cross(up, view);
        right.normalize();
        up.cross(view, right);
        
        int[] viewport = new int[4];
        gl.glGetIntegerv(GL_VIEWPORT, viewport, 0);

        renderPolygons(gl, testPolygonProgram, gr, ar, aoVolumeTex, sphereCount, view, up, right, viewport);
        
        if (renderSelectedSphere) {
            gl.glColor4f(1f, 1f, 0f, 1f);
            float[] positions = dynamics.getMolecule().getAtomPositions(snapshot);
            gl.glUseProgram(defaultProgram);
            gl.glPushMatrix();
            gl.glTranslatef(positions[selectedSphere * 3], positions[selectedSphere * 3 + 1], positions[selectedSphere * 3 + 2]);
            glut.glutSolidSphere(dynamics.getMolecule().getAtom(selectedSphere).r, 16, 16);
            gl.glPopMatrix();
        }
        
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        gl.glBeginQuery(GL_TIME_ELAPSED, raycastTrianglesElapsedQuery);
        
        renderTriangles(gl, testTriangleProgram, gr, ar, aoVolumeTex, triangleCount, view, up, right, viewport);
        
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        gl.glBeginQuery(GL_TIME_ELAPSED, raycastToriElapsedQuery);
        
        renderTori(gl, testTorusProgram, gr, ar, aoVolumeTex, torusCount, view, up, right, viewport);
        
        gl.glEndQuery(GL_TIME_ELAPSED);
        
        gl.glPopMatrix();
        
        gl.glUseProgram(defaultProgram);
        Utils.setUniform(gl, defaultProgram, "window", viewport[2], viewport[3]);
        
        // DEBUG planes
        //drawTorusPlanes(gl);
        
        // DEBUG sorting vectors
        //drawTorusVectors(gl);
        
        // DEBUG spherical polygon
        //polygon.display(gl, eye, center);
        
        // DEBUG AABB
        /*gl.glPushMatrix();
        gl.glTranslatef(-4f, -4f, -4f);
        Utils.drawAABB(gl, aabbMin, aabbSize);
        gl.glPopMatrix();*/
        
        // DEBUG acetone
        gl.glUseProgram(defaultProgram);
        //renderAcetone(gl, acetone);
        
        if (renderPoint) {
            Utils.drawPoint(gl, point, 2f);
        }
        
        // render drugs
        Point3f moleculeCenter = new Point3f(aabbMin);
        moleculeCenter.x += aabbSize / 2f - 4f;
        moleculeCenter.y += aabbSize / 2f - 4f;
        moleculeCenter.z += aabbSize / 2f - 4f;
        Point3f cavityCenter = new Point3f(48f, 44f, 30f);
        Point3f drugCenter = new Point3f();
        for (Drug drug : dynamics.getDrugs()) {
            drug.getCenter(snapshot, drugCenter);
            if (cavityCenter.distance(drugCenter) <= 15f) {
                renderAcetone(gl, drug, viewport);
            }
        }
        
        //gl.glPopMatrix();
        
        gl.glBeginQuery(GL_TIME_ELAPSED, resolveElapsedQuery);
        
        // resolve A-buffer
        gl.glUseProgram(resolveProgram);
        
        Utils.clearCounter(gl, countersBuffer, 0);
        Utils.clearCounter(gl, countersBuffer, 4);
        Utils.clearCounter(gl, countersBuffer, 8); // maxFragmentCount
        
        Utils.setUniform(gl, resolveProgram, "window", viewport[2], viewport[3]);
        Utils.setUniform(gl, resolveProgram, "maxNumFragments", MAX_FRAGMENTS);
        
        // draw fullscreen quad
        gl.glBegin(GL_QUADS);
        gl.glVertex2f( 1f,  1f);
        gl.glVertex2f(-1f,  1f);
        gl.glVertex2f(-1f, -1f);
        gl.glVertex2f( 1f, -1f);
        gl.glEnd();
        gl.glMemoryBarrier((int) GL_ALL_BARRIER_BITS);
        
        int pixelCount = Utils.getCounter(gl, countersBuffer, 0);
        int overdrawCount = Utils.getCounter(gl, countersBuffer, 4);
        int maxFragmentCount = Utils.getCounter(gl, countersBuffer, 8);
        if (overdrawCount > 0) {
            System.err.println("Warning: Overdraw in pixels: " + overdrawCount);
        }
        
        gl.glEndQuery(GL_TIME_ELAPSED);
        //drawResults(gl.getGL2());
        
        //System.out.println("Before CLGraph");
        //clGraph.test();
        //System.out.println("After CLGraph");
        
        // INFO: This is needed due to bug in JOGL
        gl.glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        
        if (PERFORMANCE_TESTS_ENABLED) {
            array.profileArray(gl, atomCount * MAX_NEIGHBORS);
            clArray.run(gl, atomCount * MAX_NEIGHBORS);
        }
        
        // wait for the query to complete
        if (writePerformanceInfo) {
            writePerformanceInfo = false;
            // write primitive counts
            System.out.println("Spheres: " + sphereCount);
            System.out.println("Triangles: " + triangleCount);
            System.out.println("Tori: " + torusCount);
            System.out.println("Isolated tori: " + isolatedTorusCount);
            // write timer results
            IntBuffer resultBuffer = Buffers.newDirectIntBuffer(1);
            while (resultBuffer.get(0) != 1) {
                gl.glGetQueryObjectiv(resolveElapsedQuery, GL_QUERY_RESULT_AVAILABLE, resultBuffer);
            }
            // get the query result
            int hashElapsed = Utils.getTimeElapsed(gl, hashElapsedQuery);
            int neighborsElapsed = Utils.getTimeElapsed(gl, neighborsElapsedQuery);
            int removeElapsed = Utils.getTimeElapsed(gl, removeElapsedQuery);
            int arcsElapsed = Utils.getTimeElapsed(gl, arcsElapsedQuery);
            int writeElapsed = Utils.getTimeElapsed(gl, writeElapsedQuery);
            int miscsElapsed = Utils.getTimeElapsed(gl, miscsElapsedQuery);
            int singularityElapsed = Utils.getTimeElapsed(gl, singularityElapsedQuery);
            int raycastSpheresElapsed = Utils.getTimeElapsed(gl, raycastSpheresElapsedQuery);
            int raycastTrianglesElapsed = Utils.getTimeElapsed(gl, raycastTrianglesElapsedQuery);
            int raycastToriElapsed = Utils.getTimeElapsed(gl, raycastToriElapsedQuery);
            int resolveElapsed = Utils.getTimeElapsed(gl, resolveElapsedQuery);
            System.out.println("Time elapsed (hash): " + hashElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (neighbors): " + neighborsElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (remove): " + removeElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (arcs): " + arcsElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (write): " + writeElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (miscs): " + miscsElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (singularity): " + singularityElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (ray-cast spheres): " + raycastSpheresElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (ray-cast triangles): " + raycastTrianglesElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (ray-cast tori): " + raycastToriElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (resolve): " + resolveElapsed / 1000000.0 + " ms");
            // check A-buffer capacity
            //int fragmentCount = getAtomicCounter(gl, atomicCountersBuffer);
            /*IntBuffer fragCount = Buffers.newDirectIntBuffer(1);
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, fragmentsIndexBuffer);
            gl.glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, Buffers.SIZEOF_INT, fragCount);
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
            int fragmentCount = fragCount.get(0);
            if (fragmentCount > MAX_ABUFFER_FRAGMENTS * 3 / 4) {
                System.out.println("A-buffer fragment count (> 75%%): " + fragmentCount);
            }
            if (fragmentCount > MAX_ABUFFER_FRAGMENTS * 9 / 10) {
                System.err.println("Warning: A-buffer fragment count (> 90%%): " + fragmentCount);
            }*/
            System.out.println("Max. fragments: " + maxFragmentCount);
            System.out.println("Coverage: " + 100f * ((float) pixelCount) / (width * height));
            // testing
            int cbElapsed = hashElapsed + neighborsElapsed + removeElapsed + arcsElapsed;
            int raycastElapsed = raycastSpheresElapsed + raycastTrianglesElapsed + raycastToriElapsed;
            System.out.println("Time elapsed (CB): " + cbElapsed / 1000000.0 + " ms");
            System.out.println("Time elapsed (Ray-casting): " + raycastElapsed / 1000000.0 + " ms");
        }
        
        if (writeResults) {
            writeResults = false;
            try {
                //writeAtomsVisible(gl, atoms.size());
                writeGrid(gl, "probe-grid.txt");
                //writeNeighbors(gl, probeNeighborsBuffer, probeNeighborCountsBuffer, triangleCount, "probe-neighbors.txt");
                //writeFragments(gl, viewport[2], viewport[3]);
                writeTriangles(gl, triangleCount);
                writeTori(gl, torusCount);
                writePolygons(gl, sphereCount);
                writeSphereIsolated(gl);
                //writeDebugi(gl, 2);
                //writeDebug4f(gl, 4);
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
        
        //System.out.println("End display(...)");
    }
    
    private void renderPolygons(GL4bc gl, int program, GPUGraph.Result gr, Area.Result ar, int aoVolumeTex, int count,
            Vector3f view, Vector3f up, Vector3f right, int[] viewport) {
        gl.glUseProgram(program);
        
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, debugBuffer);
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, IntBuffer.wrap(new int[] { 0 }));
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        
        gl.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, DEBUG_BUFFER_INDEX, debugBuffer);
        gl.glBindBufferBase(GL_UNIFORM_BUFFER, MINMAX_CAVITY_AREA_BUFFER_INDEX, ar.getMinMaxBuffer());
        
        gl.glColor4f(sphereColor.getRed() / 255f, sphereColor.getGreen() / 255f, sphereColor.getBlue() / 255f, alpha);
        
        gl.glActiveTexture(GL_TEXTURE0);
        gl.glBindTexture(GL_TEXTURE_BUFFER, gr.getCirclesTex());
        //gl.glActiveTexture(GL_TEXTURE1);
        //gl.glBindTexture(GL_TEXTURE_BUFFER, gr.getCirclesStartTex());
        //gl.glActiveTexture(GL_TEXTURE2);
        //gl.glBindTexture(GL_TEXTURE_BUFFER, gr.getCirclesLengthTex());
        gl.glActiveTexture(GL_TEXTURE3);
        gl.glBindTexture(GL_TEXTURE_BUFFER, surfaceEdgesCircleTex);
        gl.glActiveTexture(GL_TEXTURE4);
        gl.glBindTexture(GL_TEXTURE_BUFFER, surfaceEdgesLineTex);
        gl.glActiveTexture(GL_TEXTURE5);
        gl.glBindTexture(GL_TEXTURE_1D, ar.getAreasTexture());
        gl.glActiveTexture(GL_TEXTURE6);
        gl.glBindTexture(GL_TEXTURE_BUFFER, sphereIsolatedCountsTex);
        gl.glActiveTexture(GL_TEXTURE7);
        gl.glBindTexture(GL_TEXTURE_BUFFER, sphereIsolatedVSTex);
        gl.glActiveTexture(GL_TEXTURE8);
        gl.glBindTexture(GL_TEXTURE_3D, aoVolumeTex);
        
        Utils.setSampler(gl, program, "circlesTex", 0);
        //Utils.setSampler(gl, program, "circlesStartTex", 1);
        //Utils.setSampler(gl, program, "circlesLengthTex", 2);
        Utils.setSampler(gl, program, "edgesCircleTex", 3);
        Utils.setSampler(gl, program, "edgesLineTex", 4);
        Utils.setSampler(gl, program, "areasTex", 5);
        Utils.setSampler(gl, program, "sphereIsolatedCountsTex", 6);
        Utils.setSampler(gl, program, "sphereIsolatedVSTex", 7);
        Utils.setSampler(gl, program, "aoVolumeTex", 8);
        
        // camera
        Utils.setUniform(gl, program, "camIn", view.x, view.y, view.z);
        Utils.setUniform(gl, program, "camUp", up.x, up.y, up.z);
        Utils.setUniform(gl, program, "camRight", right.x, right.y, right.z);
        // viewport
        Utils.setUniform(gl, program, "viewport", 0f, 0f, 2f / viewport[2], 2f / viewport[3]);
        Utils.setUniform(gl, program, "window", viewport[2], viewport[3]);
        // other properties
        //Utils.setUniform(gl, program, "maxNumNeighbors", MAX_NEIGHBORS);
        Utils.setUniform(gl, program, "probeRadius", probeRadius);
        Utils.setUniform(gl, program, "sas", surfaceType == Surface.SAS ? 1 : 0); // SAS/SES
        // cavity clipping
        Utils.setUniform(gl, program, "clipCavities", clipCavities);
        Utils.setUniform(gl, program, "clipSurface", clipSurface);
        Utils.setUniform(gl, program, "surfaceLabel", gr.getSurfaceLabels()[0]);
        Utils.setUniform(gl, program, "threshold", threshold);
        // ambient occlusion & lighting
        Utils.setUniform(gl, program, "lambda", volumetricAO.getLambda());
        Utils.setUniform(gl, program, "volumeSize", aabbSize);
        Utils.setUniform(gl, program, "phong", phongLighting);
        Utils.setUniform(gl, program, "ao", ambientOcclusion);
        Utils.setUniform(gl, program, "aoExponent", aoExponent);
        Utils.setUniform(gl, program, "aoThreshold", aoThreshold);
        Utils.setUniform(gl, program, "silhouettes", silhouettes);
        Utils.setUniform(gl, program, "bfmod", backfaceModulation);
        // cavity coloring
        Utils.setUniform(gl, program, "cavityColoring", cavityColoring == Coloring.AREA ? 0 : 1);
        Utils.setUniform(gl, program, "cavityColor1", cavityColor1);
        Utils.setUniform(gl, program, "cavityColor2", cavityColor2);
        // tunnel coloring
        //Utils.setUniform(gl, program, "tunnelColor", tunnelColor);
        // clipping by isolated tori
        Utils.setUniform(gl, program, "maxSphereIsolatedTori", MAX_SPHERE_ISOLATED_TORI);
        
        gl.glEnableClientState(GL_VERTEX_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE0);
        gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        
        gl.glBindBuffer(GL_ARRAY_BUFFER, spheresArrayBuffer);
        gl.glVertexPointer(4, GL_FLOAT, 32, 0);
        gl.glClientActiveTexture(GL_TEXTURE0);
        gl.glTexCoordPointer(4, GL_INT, 32, 16);
        //gl.glClientActiveTexture(GL_TEXTURE1);
        //gl.glTexCoordPointer(4, GL_FLOAT, 48, 32);
        
        if (renderSpheres) {
            gl.glDrawArrays(GL_POINTS, 0, count);
            /*try {
                writeDebug4f(gl, 4); // TODO error in clipping by small circles
            } catch (IOException ex) {
                
            }*/
        }
        
        gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        gl.glDisableClientState(GL_VERTEX_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE0);
        gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        
        // sync A-buffer
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }
    
    private void renderTriangles(GL4bc gl, int program, GPUGraph.Result gr, Area.Result ar, int aoVolumeTex, int count,
            Vector3f view, Vector3f up, Vector3f right, int[] viewport) {
        gl.glUseProgram(program);
        
        gl.glColor4f(triangleColor.getRed() / 255f, triangleColor.getGreen() / 255f, triangleColor.getBlue() / 255f, alpha);
        
        gl.glBindBufferBase(GL_UNIFORM_BUFFER, MINMAX_CAVITY_AREA_BUFFER_INDEX, ar.getMinMaxBuffer());
        
        gl.glActiveTexture(GL_TEXTURE0);
        gl.glBindTexture(GL_TEXTURE_BUFFER, probeNeighborCountsTex);
        gl.glActiveTexture(GL_TEXTURE1);
        gl.glBindTexture(GL_TEXTURE_BUFFER, probeNeighborProbesTex);
        gl.glActiveTexture(GL_TEXTURE2);
        gl.glBindTexture(GL_TEXTURE_BUFFER, surfaceVerticesTex);
        gl.glActiveTexture(GL_TEXTURE3);
        gl.glBindTexture(GL_TEXTURE_1D, ar.getAreasTexture());
        gl.glActiveTexture(GL_TEXTURE4);
        gl.glBindTexture(GL_TEXTURE_3D, aoVolumeTex);
        
        Utils.setSampler(gl, program, "neighborCountsTex", 0);
        Utils.setSampler(gl, program, "neighborProbesTex", 1);
        Utils.setSampler(gl, program, "labelsTex", 2);
        Utils.setSampler(gl, program, "areasTex", 3);
        Utils.setSampler(gl, program, "aoVolumeTex", 4);
        
        Utils.setUniform(gl, program, "camIn", view.x, view.y, view.z);
        Utils.setUniform(gl, program, "camUp", up.x, up.y, up.z);
        Utils.setUniform(gl, program, "camRight", right.x, right.y, right.z);
        Utils.setUniform(gl, program, "viewport", 0f, 0f, 2f / viewport[2], 2f / viewport[3]);
        Utils.setUniform(gl, program, "window", viewport[2], viewport[3]);
        Utils.setUniform(gl, program, "maxNumNeighbors", MAX_NEIGHBORS);
        Utils.setUniform(gl, program, "probeRadius", probeRadius);
        Utils.setUniform(gl, program, "surfaceLabel", gr.getSurfaceLabels()[0]);
        Utils.setUniform(gl, program, "clipSurface", clipSurface);
        Utils.setUniform(gl, program, "clipCavities", clipCavities);
        Utils.setUniform(gl, program, "lambda", volumetricAO.getLambda());
        Utils.setUniform(gl, program, "volumeSize", aabbSize);
        Utils.setUniform(gl, program, "phong", phongLighting);
        Utils.setUniform(gl, program, "ao", ambientOcclusion);
        Utils.setUniform(gl, program, "aoExponent", aoExponent);
        Utils.setUniform(gl, program, "aoThreshold", aoThreshold);
        Utils.setUniform(gl, program, "silhouettes", silhouettes);
        Utils.setUniform(gl, program, "bfmod", backfaceModulation);
        Utils.setUniform(gl, program, "threshold", threshold);
        Utils.setUniform(gl, program, "cavityColoring", cavityColoring == Coloring.AREA ? 0 : 1);
        Utils.setUniform(gl, program, "cavityColor1", cavityColor1);
        Utils.setUniform(gl, program, "cavityColor2", cavityColor2);
        //Utils.setUniform(gl, program, "tunnelColor", tunnelColor);
        
        gl.glEnableClientState(GL_VERTEX_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE0);
        gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE1);
        gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE2);
        gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        
        gl.glBindBuffer(GL_ARRAY_BUFFER, trianglesArrayBuffer);
        gl.glVertexPointer(4, GL_FLOAT, 64, 0);
        gl.glClientActiveTexture(GL_TEXTURE0);
        gl.glTexCoordPointer(4, GL_FLOAT, 64, 16);
        gl.glClientActiveTexture(GL_TEXTURE1);
        gl.glTexCoordPointer(4, GL_FLOAT, 64, 32);
        gl.glClientActiveTexture(GL_TEXTURE2);
        gl.glTexCoordPointer(4, GL_FLOAT, 64, 48);
        
        if (surfaceType == Surface.SES && renderTriangles) {
            gl.glDrawArrays(GL_POINTS, 0, count);
            // Debug
            if (renderSelectedTriangle) {
                gl.glColor4f(1f, 1f, 0f, 1f);
                gl.glDrawArrays(GL_POINTS, Math.min(selectedTriangle, count - 1), 1);
            }
        }
        
        gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        gl.glDisableClientState(GL_VERTEX_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE0);
        gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE1);
        gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE2);
        gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT); // A-buffer
    }
    
    private void renderTori(GL4bc gl, int program, GPUGraph.Result gr, Area.Result ar, int aoVolumeTex, int count,
            Vector3f view, Vector3f up, Vector3f right, int[] viewport) {
        // draw tori
        gl.glUseProgram(program);
        
        gl.glColor4f(torusColor.getRed() / 255f, torusColor.getGreen() / 255f, torusColor.getBlue() / 255f, alpha);
        
        gl.glBindBufferBase(GL_UNIFORM_BUFFER, MINMAX_CAVITY_AREA_BUFFER_INDEX, ar.getMinMaxBuffer());
        
        gl.glActiveTexture(GL_TEXTURE0);
        gl.glBindTexture(GL_TEXTURE_BUFFER, surfaceEdgesTex);
        gl.glActiveTexture(GL_TEXTURE1);
        gl.glBindTexture(GL_TEXTURE_BUFFER, surfaceVerticesTex);
        gl.glActiveTexture(GL_TEXTURE2);
        gl.glBindTexture(GL_TEXTURE_1D, ar.getAreasTexture());
        gl.glActiveTexture(GL_TEXTURE3);
        gl.glBindTexture(GL_TEXTURE_3D, aoVolumeTex);
        
        Utils.setSampler(gl, program, "edgesTex", 0);
        Utils.setSampler(gl, program, "labelsTex", 1);
        Utils.setSampler(gl, program, "areasTex", 2);
        Utils.setSampler(gl, program, "aoVolumeTex", 3);
        
        Utils.setUniform(gl, program, "camIn", view.x, view.y, view.z);
        Utils.setUniform(gl, program, "camUp", up.x, up.y, up.z);
        Utils.setUniform(gl, program, "camRight", right.x, right.y, right.z);
        Utils.setUniform(gl, program, "viewport", 0f, 0f, 2f / viewport[2], 2f / viewport[3]);
        Utils.setUniform(gl, program, "window", viewport[2], viewport[3]);
        Utils.setUniform(gl, program, "probeRadius", probeRadius);
        Utils.setUniform(gl, program, "surfaceLabel", gr.getSurfaceLabels()[0]);
        Utils.setUniform(gl, program, "clipSurface", clipSurface);
        Utils.setUniform(gl, program, "clipCavities", clipCavities);
        Utils.setUniform(gl, program, "selectCavity", renderSelectedCavity);
        Utils.setUniform(gl, program, "cavityLabel", selectedCavity);
        Utils.setUniform(gl, program, "lambda", volumetricAO.getLambda());
        Utils.setUniform(gl, program, "volumeSize", aabbSize);
        Utils.setUniform(gl, program, "phong", phongLighting);
        Utils.setUniform(gl, program, "ao", ambientOcclusion);
        Utils.setUniform(gl, program, "aoExponent", aoExponent);
        Utils.setUniform(gl, program, "aoThreshold", aoThreshold);
        Utils.setUniform(gl, program, "silhouettes", silhouettes);
        Utils.setUniform(gl, program, "bfmod", backfaceModulation);
        Utils.setUniform(gl, program, "threshold", threshold);
        Utils.setUniform(gl, program, "cavityColoring", cavityColoring == Coloring.AREA ? 0 : 1);
        Utils.setUniform(gl, program, "cavityColor1", cavityColor1);
        Utils.setUniform(gl, program, "cavityColor2", cavityColor2);
        //Utils.setUniform(gl, program, "tunnelColor", tunnelColor);
        
        gl.glEnableClientState(GL_VERTEX_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE0);
        gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE1);
        gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE2);
        gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE3);
        gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        
        gl.glBindBuffer(GL_ARRAY_BUFFER, toriArrayBuffer);
        gl.glVertexPointer(4, GL_FLOAT, SIZEOF_TORUS, 0);
        gl.glClientActiveTexture(GL_TEXTURE0);
        gl.glTexCoordPointer(4, GL_FLOAT, SIZEOF_TORUS, 16);
        gl.glClientActiveTexture(GL_TEXTURE1);
        gl.glTexCoordPointer(4, GL_FLOAT, SIZEOF_TORUS, 32);
        gl.glClientActiveTexture(GL_TEXTURE2);
        gl.glTexCoordPointer(4, GL_FLOAT, SIZEOF_TORUS, 48);
        gl.glClientActiveTexture(GL_TEXTURE3);
        gl.glTexCoordPointer(4, GL_FLOAT, SIZEOF_TORUS, 64);
        
        if (surfaceType == Surface.SES && renderTori) {
            gl.glDrawArrays(GL_POINTS, 0, count);
            // Debug
            if (renderSelectedTorus) {
                gl.glColor4f(1f, 1f, 0f, 1f);
                gl.glDrawArrays(GL_POINTS, Math.min(selectedTorus, count - 1), 1);
            }
        }
        
        gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        gl.glDisableClientState(GL_VERTEX_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE0);
        gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE1);
        gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE2);
        gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        gl.glClientActiveTexture(GL_TEXTURE3);
        gl.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT); // A-buffer
    }
    
    private void renderAcetone(GL4 gl, Drug drug, int[] viewport) {
        float[] positions = drug.getAtomPositions(snapshot);
        float[] nextPositions = drug.getAtomPositions((snapshot + 1) % drug.getSnapshotCount());
        //Vector3f pos = new Vector3f();
        GL2 gl2 = gl.getGL2();
        //gl2.glPushAttrib(GL_ALL_ATTRIB_BITS);
        //gl2.glLineWidth(4f);
        gl2.glColor3f(1f, 0f, 0f);
        //gl2.glBegin(GL_LINES);
        int c1 = 0;
        int c2 = 4;
        int c3 = 5;
        int o1 = 9;
        /*line(gl2, positions, c1, c1 + 1); // C1-H1
        line(gl2, positions, c1, c1 + 2); // C1-H2
        line(gl2, positions, c1, c1 + 3); // C1-H3
        line(gl2, positions, c1, c2); // C1-C2
        line(gl2, positions, c2, o1); // C2-O1
        line(gl2, positions, c2, c3); // C2-C3
        line(gl2, positions, c3, c3 + 1); // C3-H4
        line(gl2, positions, c3, c3 + 2); // C3-H5
        line(gl2, positions, c3, c3 + 3); // C3-H6
        gl2.glEnd();*/
        //gl2.glPopAttrib();
        stick(gl2, positions, nextPositions, c1, c1 + 1, viewport); // C1-H1
        stick(gl2, positions, nextPositions, c1, c1 + 2, viewport); // C1-H2
        stick(gl2, positions, nextPositions, c1, c1 + 3, viewport); // C1-H3
        stick(gl2, positions, nextPositions, c1, c2, viewport); // C1-C2
        stick(gl2, positions, nextPositions, c2, o1, viewport); // C2-O1
        stick(gl2, positions, nextPositions, c2, c3, viewport); // C2-C3
        stick(gl2, positions, nextPositions, c3, c3 + 1, viewport); // C3-H4
        stick(gl2, positions, nextPositions, c3, c3 + 2, viewport); // C3-H5
        stick(gl2, positions, nextPositions, c3, c3 + 3, viewport); // C3-H6
    }
    
    private void line(GL2 gl, float[] positions, int p0, int p1) {
        gl.glVertex3f(positions[3 * p0], positions[3 * p0 + 1], positions[3 * p0 + 2]);
        gl.glVertex3f(positions[3 * p1], positions[3 * p1 + 1], positions[3 * p1 + 2]);
    }
    
    private void stick(GL2 gl, float[] positions, float[] nextPositions, int p0, int p1, int[] viewport) {
        float p0x = linearInterpolation(positions[3 * p0],     nextPositions[3 * p0],     t);
        float p0y = linearInterpolation(positions[3 * p0 + 1], nextPositions[3 * p0 + 1], t);
        float p0z = linearInterpolation(positions[3 * p0 + 2], nextPositions[3 * p0 + 2], t);
        float p1x = linearInterpolation(positions[3 * p1],     nextPositions[3 * p1], t);
        float p1y = linearInterpolation(positions[3 * p1 + 1], nextPositions[3 * p1 + 1], t);
        float p1z = linearInterpolation(positions[3 * p1 + 2], nextPositions[3 * p1 + 2], t);
        
        Vector3f dir = new Vector3f(p1x - p0x, p1y - p0y, p1z - p0z);
        Vector3f up = new Vector3f(0f, 1f, 0f);
        Vector3f axis = new Vector3f();
        
        float size = dir.length();
        dir.scale(1f / size); // normalize
        axis.cross(up, dir);
        
        gl.glPushMatrix();
        gl.glTranslatef((p0x + p1x) / 2f, (p0y + p1y) / 2f, (p0z + p1z) / 2f);
        gl.glRotatef((float) Math.toDegrees(Math.acos(dir.dot(up))), axis.x, axis.y, axis.z);
        
        gl.glUseProgram(stickProgram);
        Utils.setUniform(gl.getGL4(), stickProgram, "size", size);
        Utils.setUniform(gl.getGL4(), stickProgram, "window", viewport[2], viewport[3]);
        
        gl.glColor4f(1f, 0f, 0f, 1f);
        gl.glEnableClientState(GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL_NORMAL_ARRAY);
        
        gl.glBindBuffer(GL_ARRAY_BUFFER, capsule.getVertexArrayBuffer());
        gl.glVertexPointer(3, GL_FLOAT, 24, 0);
        gl.glNormalPointer(GL_FLOAT, 24, 12);
        
        gl.glDrawArrays(GL_TRIANGLES, 0, 3 * capsule.getTriangleCount());
        
        gl.glDisableClientState(GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL_NORMAL_ARRAY);
        
        gl.glPopMatrix();
    }
    
    private void drawSmallCircles(GL4 gl, int count) {
        gl.glBindBuffer(GL_ARRAY_BUFFER, smallCirclesArrayBuffer);
        gl.glUseProgram(smallCirclesProgram);
        
        int location = gl.glGetAttribLocation(smallCirclesProgram, "position");
        gl.glEnableVertexAttribArray(location);
        gl.glVertexAttribPointer(location, 4, GL_FLOAT, false, 0, 0);
        
        gl.glLineWidth(2.0f);
        //System.out.println("Small circles count: " + smallCirclesCount);
        for (int i = 0; i < count; i++) {
            gl.glDrawArrays(GL_LINE_STRIP, i * 13, 13);
        }
        
        gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
        gl.glDisableVertexAttribArray(location);
    }
    
    private int getAtomicCounter(GL4 gl, int buffer) {
        return getAtomicCounter(gl, buffer, 0);
    }
    
    private int getAtomicCounter(GL4 gl, int buffer, int offset) {
        IntBuffer data = Buffers.newDirectIntBuffer(1);
        gl.glBindBuffer(GL_ATOMIC_COUNTER_BUFFER, buffer);
        gl.glGetBufferSubData(GL_ATOMIC_COUNTER_BUFFER, offset, 4, data);
        gl.glBindBuffer(GL_ATOMIC_COUNTER_BUFFER, 0);
        return data.get();
    }
    
    private void clearAtomicCounter(GL4 gl, int buffer) {
        setAtomicCounter(gl, buffer, 0, 0);
    }
    
    private void clearAtomicCounter(GL4 gl, int buffer, int offset) {
        setAtomicCounter(gl, buffer, offset, 0);
    }
    
    private void setAtomicCounter(GL4 gl, int buffer, int offset, int value) {
        IntBuffer data = Buffers.newDirectIntBuffer(1);
        data.put(value);
        data.rewind();
        gl.glBindBuffer(GL_ATOMIC_COUNTER_BUFFER, buffer);
        gl.glBufferSubData(GL_ATOMIC_COUNTER_BUFFER, offset, 4, data);
        gl.glBindBuffer(GL_ATOMIC_COUNTER_BUFFER, 0);
    }
    
    private int getInteger(GL4 gl, int param) {
        int[] data = new int[1];
        gl.glGetIntegerv(param, data, 0);
        return data[0];
    }
    
    @Override
    public void reshape(GLAutoDrawable glad, int x, int y, int width, int height) {
        this.width = width;
        this.height = height;
        
        // Get GL2 interface
        GL2 gl = glad.getGL().getGL2();
        
        // Use projection matrix
	gl.glMatrixMode(GL_PROJECTION);

	// Set up perspective projection matrix
	gl.glLoadIdentity();
	glu.gluPerspective(60, ((double)width)/height, 1.0, 1000.0);

	// Part of the image where the scene will be renderer, (0, 0) is bottom left
	gl.glViewport(0, 0, width, height);

	// Use model view matrix
	gl.glMatrixMode(GL_MODELVIEW);
    }
    
    private void drawCross(GL2 gl, float size) {
        gl.glDisable(GL_LIGHTING);
        gl.glBegin(GL2.GL_LINES);
        gl.glColor3f(0f, 1f, 0f);
        gl.glVertex3f(-size, 0f, 0f);
        gl.glVertex3f(size, 0f, 0f);
        gl.glVertex3f(0f, -size, 0f);
        gl.glVertex3f(0f, size, 0f);
        gl.glVertex3f(0f, 0f, -size);
        gl.glVertex3f(0f, 0f, size);
        gl.glEnd();
        gl.glEnable(GL_LIGHTING);
    }
    
    private void drawTorusPlanes(GL2 gl) {
        gl.glPushAttrib(GL_ALL_ATTRIB_BITS);
        gl.glLineWidth(2.0f);
        
        gl.glPushMatrix();
        gl.glTranslatef(aabbMin.x - 4f, aabbMin.y - 4f, aabbMin.z - 4f);
        
        Vector4f plane1 = new Vector4f(0.302031f, 0.614192f, 0.729072f, -2.670520f);
        Vector4f plane2 = new Vector4f(-0.364167f, -0.929845f, -0.052632f, 2.414194f);
        Vector3f n1 = new Vector3f(plane1.x, plane1.y, plane1.z);
        Vector3f n2 = new Vector3f(plane2.x, plane2.y, plane2.z);
        Vector3f z1 = new Vector3f();
        Vector3f z2 = new Vector3f();
        z1.cross(n1, new Vector3f(1f, 0f, 0f));
        z1.normalize();
        z2.cross(n2, new Vector3f(1f, 0f, 0f));
        z2.normalize();
        Vector3f x1 = new Vector3f();
        Vector3f x2 = new Vector3f();
        x1.cross(n1, z1);
        x2.cross(n2, z2);
        plane1.scale(-plane1.w);
        plane2.scale(-plane2.w);
        
        gl.glPushMatrix();
        
        float[] rotMat = new float[] {
            x1.x, x1.y, x1.z, 0f,
            n1.x, n1.y, n1.z, 0f,
            z1.x, z1.y, z1.z, 0f,
            0f, 0f, 0f, 1f,
        };
        
        gl.glTranslatef(plane1.x, plane1.y, plane1.z);
        gl.glMultMatrixf(rotMat, 0);
        Utils.drawAxes(gl, 2);
        Utils.drawPlane(gl, 4);
        
        gl.glPopMatrix();
        
        rotMat = new float[] {
            x2.x, x2.y, x2.z, 0f,
            n2.x, n2.y, n2.z, 0f,
            z2.x, z2.y, z2.z, 0f,
            0f, 0f, 0f, 1f,
        };
        
        gl.glTranslatef(plane2.x, plane2.y, plane2.z);
        gl.glMultMatrixf(rotMat, 0);
        Utils.drawAxes(gl, 2);
        Utils.drawPlane(gl, 4);
        
        gl.glPopMatrix();
        gl.glPopAttrib();
    }
    
    private void drawTorusVectors(GL2 gl) {
        gl.glPushAttrib(GL_ALL_ATTRIB_BITS);
        gl.glLineWidth(2f);
        
        gl.glPushMatrix();
        gl.glTranslatef(aabbMin.x - 4f, aabbMin.y - 4f, aabbMin.z - 4f);
        gl.glTranslatef(9.066299f, 9.057201f, 14.987653f);
        
        gl.glBegin(GL_LINES);
        // vector 1
        gl.glColor3f(1f, 0f, 0f);
        gl.glVertex3f(0f, 0f, 0f);
        gl.glVertex3f(2.189359f, -1.830023f, -0.592402f);
        // vector 2
        gl.glColor3f(0f, 1f, 0f);
        gl.glVertex3f(0f, 0f, 0f);
        gl.glVertex3f(2.850398f, -0.321712f, -0.514730f);
        // vector 3
        gl.glColor3f(0f, 0f, 1f);
        gl.glVertex3f(0f, 0f, 0f);
        gl.glVertex3f(-2.824510f, 0.483595f, 0.530562f);
        // vector 4
        gl.glColor3f(1f, 0f, 1f);
        gl.glVertex3f(0f, 0f, 0f);
        gl.glVertex3f(-2.845061f, 0.360352f, 0.518643f);
        gl.glEnd();
        
        gl.glPopMatrix();
        gl.glPopAttrib();
    }
    
    public void loadDynamics(File[] files) {
        try {
            dynamics = new Dynamics(Utils.loadDynamics(files));
            atomCount = dynamics.getMolecule().getAtomCount();
            System.out.println("Atoms: " + atomCount);
            System.out.println("Snapshots: " + dynamics.getSnapshotCount());
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }
        
        preprocessMolecule(dynamics.getMolecule());
    }
    
    public void loadDynamicsFromGROMACS(File topology, File trajectory) {
        try {
            dynamics = new GromacsStructureLoader().loadDynamics(topology, trajectory, 100);
            atomCount = dynamics.getMolecule().getAtomCount();
            System.out.println("Atoms: " + atomCount);
            System.out.println("Snapshots: " + dynamics.getSnapshotCount());
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
        
        preprocessMolecule(dynamics.getMolecule());
    }
    
    public void loadDynamicsFromResource(String name, int start, int end) {
        try {
            dynamics = new Dynamics(Utils.loadDynamicsFromResource(name, start, end));
            atomCount = dynamics.getMolecule().getAtomCount();
            System.out.println("Atoms: " + atomCount);
            System.out.println("Snapshots: " + dynamics.getSnapshotCount());
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }
        
        preprocessMolecule(dynamics.getMolecule());
    }
    
    private void preprocessMolecule(Molecule dynamics) {
        snapshot = 0;
        lastUpdateTime = -1L;
        
        AABB bb = preprocessAtoms(dynamics);
        updateBoundingBox(bb);
        
        uploaded = false;
    }
    
    private static AABB preprocessAtoms(Molecule dynamics) {
        float[] first = dynamics.getAtomPositions(0);
        Point3f min = new Point3f(first[0], first[1], first[2]);
        Point3f max = new Point3f(min);
        for (float[] snapshot : dynamics.getSnapshots()) {
            for (int i = 0; i < dynamics.getAtomCount(); i++) {
                min.x = Math.min(min.x, snapshot[3 * i]);
                min.y = Math.min(min.y, snapshot[3 * i + 1]);
                min.z = Math.min(min.z, snapshot[3 * i + 2]);
                max.x = Math.max(max.x, snapshot[3 * i]);
                max.y = Math.max(max.y, snapshot[3 * i + 1]);
                max.z = Math.max(max.z, snapshot[3 * i + 2]);
            }
        }
        
        Point3f center = new Point3f();
        center.add(max, min);
        center.scale(0.5f);
        //min.sub(center);
        max.sub(center);
        //float scale = 8f / Math.max(Math.max(max.x, max.y), max.z);
        //cellSize = 1f / scale;
        for (float[] snapshot : dynamics.getSnapshots()) {
            for (int i = 0; i < dynamics.getAtomCount(); i++) {
                /*atom.x = scale * (atom.x - center.x);
                atom.y = scale * (atom.y - center.y);
                atom.z = scale * (atom.z - center.z);
                atom.w = scale * atom.w;*/
                snapshot[3 * i]     = snapshot[3 * i]     - min.x + 4.0f;
                snapshot[3 * i + 1] = snapshot[3 * i + 1] - min.y + 4.0f;
                snapshot[3 * i + 2] = snapshot[3 * i + 2] - min.z + 4.0f;
            }
        }
        
        return new AABB(min, max);
    }
    
    private static class AABB {
        
        public Point3f min;
        public Point3f max;

        public AABB(Point3f min, Point3f max) {
            this.min = min;
            this.max = max;
        }
        
    }

    private void updateBoundingBox(AABB bb) {
        aabbMin = new Point3f(bb.min);
        aabbSize = 2 * Math.max(Math.max(bb.max.x, bb.max.y), bb.max.z) + 8f;
        cellSize = aabbSize / GRID_SIZE;
    }
    
    public void pan(float speed) {
        totalPan += speed;
        updateCamera();
    }

    public void tilt(float speed) {
        totalTilt += speed;
        updateCamera();
    }

    public void move(float speed) {
        Vector3f view = new Vector3f();
        view.sub(center, eye);
        view.scale(speed);
        eye.add(view);
        updateCamera();
    }
    
    public void strafe(float speed) {
        Vector3f up = new Vector3f(0f, 1f, 0f);
        Vector3f view = new Vector3f();
        Vector3f side = new Vector3f();
        view.sub(center, eye);
        side.cross(view, up);
        side.normalize();
        side.scale(speed);
        eye.add(side);
        updateCamera();
    }
    
    private void updateCamera() {
        float afterTiltX = 0.0f; // Not used. Only to make things clearer
        float afterTiltY = (float) Math.sin(totalTilt);
        float afterTiltZ = (float) Math.cos(totalTilt);

        Vector3f view = new Vector3f(); 
        view.x = (float) Math.sin(totalPan) * afterTiltZ;
        view.y = afterTiltY;
        view.z = (float) Math.cos(totalPan) * afterTiltZ;

        center.sub(eye, view);
    }
    
    public void writeResults() {
        writeResults = true;
        gpuGraph.writeResults();
        volumetricAO.writeResults();
        area.writeResults();
        array.writeResults();
    }
    
    public void writePerformanceInfo() {
        writePerformanceInfo = true;
        clArcs.writePerformanceInfo();
        gpuGraph.writePerformanceInfo();
        volumetricAO.writePerformanceInfo();
        area.writePerformanceInfo();
        if (PERFORMANCE_TESTS_ENABLED) {
            array.writePerformanceInfo();
            clArray.writePerformanceInfo();
        }
    }
    
    public void update() {
        update = true;
    }
    
    public void updateSurfaceGraph() {
        updateSurfaceGraph = true;
    }
    
    public void startDynamics() {
        lastUpdateTime = System.currentTimeMillis();
        running = true;
    }
    
    public void stopDynamics() {
        running = false;
    }
    
    public boolean isDynamicsRunning() {
        return running;
    }
    
    public void setRenderingMode(int mode) {
        if (mode == 0) {
            testTriangleProgram = triangleProgram;
            testTorusProgram = torusProgram;
            testPolygonProgram = polygonProgram;
        } else {
            testTriangleProgram = kroneTriangleProgram;
            testTorusProgram = kroneTorusProgram;
            testPolygonProgram = kronePolygonProgram;
        }
    }
    
    private void updateAtomPositions(GL4 gl) {
        t = 0f;
        long time = System.currentTimeMillis();
        if (lastUpdateTime > 0) {
            long diff = time - lastUpdateTime;
            int snapshotDiff = (int) (diff * speed / 1000L);
            if (snapshotDiff > 0) {
                snapshot = (snapshot + snapshotDiff) % dynamics.getSnapshotCount();
                diff = diff - (long) (snapshotDiff * 1000L / speed);
                lastUpdateTime = time;
            }
            t = ((float) diff * speed) / 1000f;
        } else {
            // dynamics started time
            lastUpdateTime = time;
        }
        
        int nextSnapshot = (snapshot + 1) % dynamics.getSnapshotCount();
        Molecule molecule = dynamics.getMolecule();
        float[] positions = molecule.getAtomPositions(snapshot);
        float[] nextPositions = molecule.getAtomPositions(nextSnapshot);
        for (int i = 0; i < molecule.getAtomCount(); i++) {
            int offset = 3 * i;
            atomsPos.put(linearInterpolation(positions[offset],     nextPositions[offset],     t));
            atomsPos.put(linearInterpolation(positions[offset + 1], nextPositions[offset + 1], t));
            atomsPos.put(linearInterpolation(positions[offset + 2], nextPositions[offset + 2], t));
            // copy radius
            atomsPos.put(molecule.getAtom(i).r);
        }
        atomsPos.rewind();
        
        // update atoms buffer
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, atomsBuffer);
        gl.glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, atomsPos.capacity() * Buffers.SIZEOF_FLOAT, atomsPos);
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    private static float linearInterpolation(float a, float b, float t) {
        return (1f - t) * a + t * b;
    }
    
    private void writeGrid(GL4 gl, String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // read counts
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, gridCountsBuffer);
            ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            int counts[] = new int[CELL_COUNT];
            data.asIntBuffer().get(counts);
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            // write counts and indices
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, gridIndicesBuffer);
            IntBuffer indices = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            Set<Integer> cellIndices = new TreeSet<>();
            for (int i = 0; i < CELL_COUNT; i++) {
                if (counts[i] > 0) {
                    int x = i / GRID_SIZE / GRID_SIZE;
                    int y = (i / GRID_SIZE) % GRID_SIZE;
                    int z = i % GRID_SIZE;
                    writer.append(String.format("[%2d,%2d,%2d] (%2d): ", x, y, z, counts[i]));
                    cellIndices.clear();
                    for (int j = 0; j < counts[i]; j++) {
                        cellIndices.add(indices.get(MAX_CELL_ATOMS * i + j));
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
    
    private void writeNeighbors(GL4 gl, int neighborsBuffer, int neighborCountsBuffer, int sphereCount,
            String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
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
                        neighborIndices.add(neighbors.get(MAX_NEIGHBORS * i + j));
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
    
    private void writeArcs(GL4 gl) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("arcs.txt"))) {
            // read counts
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, neighborCountsBuffer);
            ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            int neighborCounts[] = new int[atomCount];
            data.asIntBuffer().get(neighborCounts);
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            // write arcs
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcCountsBuffer);
            IntBuffer arcsCounts = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            for (int i = 0; i < atomCount; i++) {
                int totalArcs = 0;
                List<Integer> counts = new ArrayList<>();
                for (int j = 0; j < neighborCounts[i]; j++) {
                    int count = arcsCounts.get(i * MAX_NEIGHBORS + j);
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
        
        private int color;
        private float depth;

        public Fragment(int color, float depth) {
            this.color = color;
            this.depth = depth;
        }
        
        @Override
        public int compareTo(Fragment other) {
            return (int)Math.signum(depth - other.depth);
        }
        
    }
    
    private void writeFragments(GL4 gl, int width, int height) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("fragments.txt"))) {
            // write A-Buffer dimensions
            writer.append("width: " + width + ", height: " + height);
            writer.newLine();
            // read fragment indices
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, fragmentsIndexBuffer);
            ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            int indices[] = new int[MAX_ABUFFER_PIXELS];
            data.asIntBuffer().get(indices);
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            // write fragments
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, fragmentsBuffer);
            int stride = SIZEOF_FRAGMENT;
            ByteBuffer fragments = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            List<Fragment> frags = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    frags.clear();
                    int index = indices[y * width + x];
                    if (index == INVALID_INDEX) {
                        continue;
                    }
                    while (index != INVALID_INDEX) {
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
    
    private void writeArcHashes(GL4 gl, int size) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("hashes.txt"))) {
            // read arcs
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcsBuffer);
            ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            float arcs[] = new float[MAX_TOTAL_ARCS * 4];
            data.asFloatBuffer().get(arcs);
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            // write hashes
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, arcHashesBuffer);
            int stride = SIZEOF_HASH;
            ByteBuffer hashes = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < size; i++) {
                int key = hashes.getInt(i * stride);
                if (key != INVALID_KEY) {
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
    
    private void writeAtomsVisible(GL4 gl, int count) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("atoms.txt"))) {
            // write tori
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, atomsVisibleBuffer);
            IntBuffer atomsVisible = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            for (int i = 0; i < count; i++) {
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
    
    private void writeTriangles(GL4 gl, int count) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("triangles.txt"))) {
            // write tori
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, trianglesArrayBuffer);
            ByteBuffer triangleArray = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < count; i++) {
                Vector4f position = getVec4(triangleArray, i * SIZEOF_TRIANGLE);
                writer.append(String.format("%4d: ", i));
                writer.append(String.format("position: [%f %f %f %f]", position.x, position.y, position.z, position.w));
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    private void writeTori(GL4 gl, int count) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("tori.txt"))) {
            // write tori
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, toriArrayBuffer);
            ByteBuffer toriArray = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < count; i++) {
                Vector4f position = getVec4(toriArray, i * SIZEOF_TORUS);
                float operation = toriArray.getFloat(i * SIZEOF_TORUS + 28);
                String op = (operation > 0f) ? "AND" : (operation < 0f) ? "OR " : "ISOLATED";
                Vector4f plane1 = getVec4(toriArray, i * SIZEOF_TORUS + 48);
                Vector4f plane2 = getVec4(toriArray, i * SIZEOF_TORUS + 64);
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
    
    private void writePolygons(GL4 gl, int count) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("polygons.txt"))) {
            // write tori
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, spheresArrayBuffer);
            ByteBuffer polygonsArray = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < count; i++) {
                int index = polygonsArray.getInt(i * SIZEOF_POLYGON + 16);
                int label = polygonsArray.getInt(i * SIZEOF_POLYGON + 20);
                int circleStart = polygonsArray.getInt(i * SIZEOF_POLYGON + 24);
                int circleLength = polygonsArray.getInt(i * SIZEOF_POLYGON + 28);
                Vector4f plane = getVec4(polygonsArray, i * SIZEOF_POLYGON + 32);
                writer.append(String.format("%4d: ", i));
                writer.append(String.format("index: %4d, ", index));
                writer.append(String.format("label: %4d, ", label));
                writer.append(String.format("circle: [%6d, %2d]", circleStart, circleLength));
                if (plane.x * plane.x + plane.y * plane.y + plane.z * plane.z > 0f) {
                    writer.append(String.format(", plane: [%f, %f, %f, %f]", plane.x, plane.y, plane.z, plane.w));
                }
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    private void writeSphereIsolated(GL4 gl) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("sphere-isolated.txt"))) {
            // read counts
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, sphereIsolatedCountsBuffer);
            ByteBuffer data = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            int counts[] = new int[dynamics.getMolecule().getAtomCount()];
            data.asIntBuffer().get(counts);
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
            // write counts and indices
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, sphereIsolatedVSBuffer);
            ByteBuffer vsData = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < counts.length; i++) {
                writer.append(String.format("%4d (%2d): ", i, counts[i]));
                for (int j = 0; j < counts[i]; j++) {
                    Vector4f vs = getVec4(vsData, (i * MAX_SPHERE_ISOLATED_TORI + j) * SIZEOF_VEC4);
                    writer.append(String.format("vs: [%f, %f, %f, %f], ", vs.x, vs.y, vs.z, vs.w));
                }
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    private void writeDebugi(GL4 gl, int count) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("debug.txt"))) {
            // write debug
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, debugBuffer);
            IntBuffer debug = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
            for (int i = 0; i < count; i++) {
                writer.append(String.format("%4d: %8d", i, debug.get(i)));
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    private void writeDebug4f(GL4 gl, int count) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("debug.txt"))) {
            // write debug
            gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, debugBuffer);
            ByteBuffer debug = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY);
            for (int i = 0; i < count; i++) {
                Vector4f v = getVec4(debug, i * 16);
                writer.append(String.format("%4d: [%f %f %f %f]", i, v.x, v.y, v.z, v.w));
                writer.newLine();
            }
            gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        }
    }
    
    private Vector4f getVec4(ByteBuffer buffer, int index) {
        Vector4f v = new Vector4f();
        v.x = buffer.getFloat(index);
        v.y = buffer.getFloat(index + 4);
        v.z = buffer.getFloat(index + 8);
        v.w = buffer.getFloat(index + 12);
        return v;
    }
    /*
    private int[] neighborsCount;
    private int[] neighbors;
    private Vector4f[] smallCircles;
    
    private void countSmallCircles() {
        neighborsCount = new int[atoms.size()];
        neighbors = new int[atoms.size() * MAX_NEIGHBORS];
        smallCircles = new Vector4f[atoms.size() * MAX_NEIGHBORS];
        
        // find small circles (brute force)
        Arrays.fill(neighborsCount, 0);
        Arrays.fill(neighbors, -1);
        for (int i = 0; i < atoms.size(); i++) {
            int count = 0;
            for (int j = 0; j < atoms.size(); j++) {
                if (i == j) {
                    continue;
                }
                Vector4f atom = atoms.get(i);
                Vector4f other = atoms.get(j);
                Vector4f vec = new Vector4f();
                vec.sub(other, atom);
                vec.w = 0f;
                float dist = vec.length();
                if (dist < atom.w + other.w + 2 * probeRadius) {
                    neighbors[i * MAX_NEIGHBORS + count] = j;
                    Vector4f smallCircle = new Vector4f();
                    float r = ((atom.w + probeRadius) * (atom.w + probeRadius))
                        + (dist * dist)
                        - ((other.w + probeRadius) * (other.w + probeRadius));
                    r = r / (2.0f * dist * dist);
                    // set small circle
                    vec.scale(r);
                    smallCircle.set(vec);
                    smallCircle.w = (float) Math.sqrt(((atom.w + probeRadius) * (atom.w + probeRadius)) - vec.dot(vec));
                    smallCircles[i * MAX_NEIGHBORS + count] = smallCircle;
                    count++;
                }
            }
            neighborsCount[i] = count;
        }
        
        // remove covered small circles
        for (int i = 0; i < atoms.size(); i++) {
            for (int jIdx = 0; jIdx < neighborsCount[i]; jIdx++) {
                Vector4f atomi = atoms.get(i);
                Vector3f pi = new Vector3f(atomi.x, atomi.y, atomi.z);
                float R = atomi.w + probeRadius;

                // flag wether j should be added (true) is cut off (false)
                boolean addJ = true;

                // the atom index of j
                int j = neighbors[i * MAX_NEIGHBORS + jIdx];
                // get small circle j
                Vector4f scj = smallCircles[i * MAX_NEIGHBORS + jIdx];
                // vj = the small circle center
                Vector3f vj = new Vector3f(scj.x, scj.y, scj.z);
                // pj = center of atom j
                Vector4f aj = atoms.get(j);
                Vector3f pj = new Vector3f(aj.x, aj.y, aj.z);

                // check j with all other neighbors k
                for (int kCnt = 0; kCnt < neighborsCount[i]; kCnt++) {
                    // don't compare the circle with itself
                    if (jIdx != kCnt) {
                        // the atom index of k
                        int k = neighbors[i * MAX_NEIGHBORS + kCnt];
                        // pk = center of atom k
                        Vector4f ak = atoms.get(k);
                        Vector3f pk = new Vector3f(ak.x, ak.y, ak.z);
                        // get small circle k
                        Vector4f sck = smallCircles[i * MAX_NEIGHBORS + kCnt];
                        // vk = the small circle center
                        Vector3f vk = new Vector3f(sck.x, sck.y, sck.z);
                        // vj * vk
                        float vjvk = vj.dot(vk);
                        // denominator
                        float denom = vj.dot(vj) * vk.dot(vk) - vjvk * vjvk;
                        // point on straight line (intersection of small circle planes)
                        Vector3f vjmvk = new Vector3f();
                        Vector3f vkmvj = new Vector3f();
                        vjmvk.sub(vj, vk);
                        vkmvj.sub(vk, vj);
                        Vector3f hvj = new Vector3f(vj);
                        Vector3f hvk = new Vector3f(vk);
                        hvj.scale(vj.dot(vjmvk) * vk.dot(vk) / denom);
                        hvk.scale(vk.dot(vkmvj) * vj.dot(vj) / denom);
                        Vector3f h = new Vector3f();
                        h.add(hvj, hvk);
                        // compute cases
                        Vector3f nj = new Vector3f();
                        nj.sub(pi, pj);
                        nj.normalize();
                        Vector3f nk = new Vector3f();
                        nk.sub(pi, pk);
                        nk.normalize();
                        Vector3f q = new Vector3f();
                        q.sub(vk, vj);
                        // if normals are the same (unrealistic, yet theoretically possible)
                        if (nj.dot(nk) == 1.0f) {
                            if (nj.dot(nk) > 0.0f) { // Redundant?
                                if (nj.dot(q) > 0.0f) {
                                    // k cuts off j --> remove j
                                    addJ = false;
                                }
                            }
                        } else if (h.length() > R) {
                            Vector3f mj = new Vector3f();
                            Vector3f mk = new Vector3f();
                            mj.sub(vj, h);
                            mk.sub(vk, h);
                            if (nj.dot(nk) > 0.0f) {
                                if (mj.dot(mk) > 0.0f && nj.dot(q) > 0.0f) {
                                    // k cuts off j --> remove j
                                    addJ = false;
                                }
                            } else {
                                if (mj.dot(mk) > 0.0f && nj.dot(q) < 0.0f) {
                                    // atom i has no contour
                                    neighborsCount[i] = 0;
                                }
                            }
                        }
                    }
                }
                // all k were tested, see if j is cut off
                if (!addJ) {
                    smallCircles[i * MAX_NEIGHBORS + jIdx].w = -1.0f;
                }
            }
        }
        
        // write statistics
        int totalNeighbors = 0;
        int totalSmallCircles = 0;
        for (int i = 0; i < atoms.size(); i++) {
            totalNeighbors += neighborsCount[i];
            for (int j = 0; j < neighborsCount[i]; j++) {
                if (smallCircles[i * MAX_NEIGHBORS + j].w >= 0f) {
                    totalSmallCircles++;
                }
            }
        }
        System.out.println("Neighbors (CPU): " + totalNeighbors);
        System.out.println("Small circles (CPU): " + totalSmallCircles);
    }
    */
}
