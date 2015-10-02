package csdemo;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLCommandQueue;
import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLEvent;
import com.jogamp.opencl.CLEventList;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLPlatform;
import com.jogamp.opencl.CLProgram;
import com.jogamp.opencl.gl.CLGLContext;
import com.jogamp.opencl.util.CLPlatformFilters;
import com.jogamp.opengl.GL4;
import edu.princeton.cs.algs4.GraphGenerator;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.mutable.MutableInt;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class CLGraph {
    
    private CLDevice device;
    private CLCommandQueue queue;
    
    private CLProgram bfsProgram;
    
    private CLBuffer<IntBuffer> vertices;
    private CLBuffer<IntBuffer> adjacency;
    private CLBuffer<IntBuffer> visited;
    private CLBuffer<IntBuffer> labels;
    private CLBuffer<IntBuffer> outVertices;
    private CLBuffer<IntBuffer> outSize;
    private CLBuffer<IntBuffer> unlabelled;
    
    private static final int MAX_VERTICES = 81920;
    private static final int MAX_FRONTIER_VERTICES = 81920;
    
    private boolean writePerformanceInfo = true;
    
    public void init(GL4 gl) {
        CLPlatform platform = CLPlatform.getDefault(CLPlatformFilters.glSharing(gl.getContext()));
        System.out.println("OpenCL platform: " + platform);
        
        device = platform.getMaxFlopsDevice(CLDevice.Type.GPU);
        CLGLContext cl = CLGLContext.create(gl.getContext(), device);
        System.out.println("OpenCL device: " + device);
        
        queue = device.createCommandQueue(CLCommandQueue.Mode.PROFILING_MODE);
        
        try {
            bfsProgram = cl.createProgram(CLGraph.class.getResourceAsStream("/resources/cl/bfs.cl")).build();
        } catch (IOException ex) {
            System.err.println("Resource loading failed. " + ex.getMessage());
            System.exit(1);
        }
        
        vertices = cl.createIntBuffer(MAX_FRONTIER_VERTICES, CLBuffer.Mem.READ_WRITE);
        adjacency = cl.createIntBuffer(MAX_VERTICES * 4, CLBuffer.Mem.READ_ONLY);
        visited = cl.createIntBuffer(MAX_VERTICES / 32, CLBuffer.Mem.READ_WRITE);
        labels = cl.createIntBuffer(MAX_VERTICES, CLBuffer.Mem.READ_WRITE);
        outVertices = cl.createIntBuffer(MAX_FRONTIER_VERTICES, CLBuffer.Mem.READ_WRITE);
        outSize = cl.createIntBuffer(1, CLBuffer.Mem.READ_WRITE);
        unlabelled = cl.createIntBuffer(1, CLBuffer.Mem.READ_WRITE);
    }
    
    public void test() {
        CLKernel iterateKernel = bfsProgram.createCLKernel("iterate");
        CLKernel groupIterateKernel = bfsProgram.createCLKernel("groupIterate");
        CLKernel unlabelledKernel = bfsProgram.createCLKernel("unlabelled");
        
        //edu.princeton.cs.algs4.Graph graph = GraphGenerator.regular(40960, 3);
        List<List<Integer>> myGraph = generateGraph(128/*, 64, 32, 32, 16*/);//adjacencyMatrix(graph);
        /*try {
            //myGraph = readGraph("test-graph-32.txt");
            writeGraph(myGraph);
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
            return;
        }*/
        
        // set static data
        IntBuffer adjancecyData = adjacency.getBuffer();
        for (int v = 0; v < myGraph.size(); v++) {
            Iterator<Integer> itAdj = myGraph.get(v).iterator();
            adjancecyData.put(v * 4 + 1, itAdj.next());
            adjancecyData.put(v * 4 + 2, itAdj.next());
            adjancecyData.put(v * 4 + 3, itAdj.next());
        }
        
        // set initial data
        IntBuffer verticesData = vertices.getBuffer();
        verticesData.put(0, 0); // put vertex 0 into frontier
        
        IntBuffer visitedData = visited.getBuffer();
        visitedData.put(0, 1); // mark first vertex visited
        for (int i = 1; i < visitedData.capacity(); i++) {
            visitedData.put(i, 0); // put visited mask 0x00000000
        }
        
        IntBuffer labelsData = labels.getBuffer();
        labelsData.put(0, 1); // put label 1 for vertex 0
        for (int v = 1; v < myGraph.size(); v++) {
            labelsData.put(v, 0);
        }
        
        IntBuffer outSizeData = outSize.getBuffer();
        outSizeData.put(0, 0); // reset output frontier size
        
        // copy static and initial data
        queue.putWriteBuffer(vertices, false)
                .putWriteBuffer(adjacency, false)
                .putWriteBuffer(visited, false)
                .putWriteBuffer(labels, false);
        
        // arguments for kernels
        iterateKernel.putArgs(vertices, adjacency, visited, labels)
                    .putArg(1)
                    .putArgs(outVertices, outSize);
        
        groupIterateKernel.putArg(adjacency)
                    .putArg(0) // start vertex
                    .putArg(1) // label
                    .putArgs(visited, labels, outVertices, outSize);
        
        unlabelledKernel.putArg(labels)
                .putArg(myGraph.size())
                .putArg(unlabelled);
        
        int start = 0;
        int label = 0;
        boolean done;
        do {
            CLEventList events = new CLEventList(128);

            label++;
            
            //System.out.println("Iteration 0: ");
            // first iteration
            iterateKernel.setArg(4, label);

            /*queue.putWriteBuffer(outSize, false)
                    .put1DRangeKernel(iterateKernel, 0, 64, 64, events)
                    .putReadBuffer(outSize, true);*/

            // optimization for small sized frontiers
            groupIterateKernel.setArg(1, start);
            groupIterateKernel.setArg(2, label);

            queue.put1DRangeKernel(groupIterateKernel, 0, 256, 256, events)
                    .putReadBuffer(outSize, true);

            int iter = 1;
            int size = outSizeData.get(0);
            while (size > 0) {
                boolean odd = (iter % 2) > 0;
                int localWorkSize = 256;
                int globalWorkSize = roundUp(localWorkSize, size);

                iterateKernel.setArg(0, odd ? outVertices : vertices);
                iterateKernel.setArg(4, size);
                iterateKernel.setArg(5, odd ? vertices : outVertices);

                //System.out.println("Iteration " + iter + ", size = " + size);
                /*CLBuffer<IntBuffer> in = odd ? outVertices : vertices;
                queue.putReadBuffer(in, true)
                        .putReadBuffer(labels, true);
                try {
                    writeFrontier(in.getBuffer(), outSizeData.get(0), "frontier/" + i + ".txt");
                    writeLabels(labels.getBuffer(), myGraph.size(), "labels/" + i + ".txt");
                } catch (IOException ex) {
                    ex.printStackTrace(System.err);
                    System.exit(2);
                }*/

                outSizeData.put(0, 0); // reset output frontier size

                //long start = System.nanoTime();

                queue.putWriteBuffer(outSize, false)
                    .put1DRangeKernel(iterateKernel, 0, globalWorkSize, localWorkSize, events)
                    .putReadBuffer(outSize, true);

                //long duration = System.nanoTime() - start;
                //System.out.println("Kernel execution: " + duration / 1000.0 / 1000.0 + " ms");

                size = outSizeData.get(0);
                iter++;
            }
            
            // search for more components
            int localWorkSize = 256;
            int globalWorkSize = roundUp(localWorkSize, myGraph.size());
            
            unlabelled.getBuffer().put(0, -1);
            
            queue.putWriteBuffer(unlabelled, false)
                    .put1DRangeKernel(unlabelledKernel, 0, globalWorkSize, localWorkSize)
                    .putReadBuffer(unlabelled, true);
            
            // performance
            long startTime = events.getEvent(0).getProfilingInfo(CLEvent.ProfilingCommand.START);
            long endTime = events.getEvent(iter - 1).getProfilingInfo(CLEvent.ProfilingCommand.END);
            long bttoTime = endTime - startTime;
            System.out.println("Time elapsed (OpenCL: bfs - btto): " + (bttoTime / 1000000.0) + " ms");
            
            start = unlabelled.getBuffer().get(0);
            done = start < 0;
        } while (!done);
        
        // read results
        queue.putReadBuffer(labels, true);
        
        // analyse labels
        Map<Integer, MutableInt> components = new HashMap<>();
        for (int v = 0; v < myGraph.size(); v++) {
            int lbl = labelsData.get(v);
            MutableInt counter = components.get(lbl);
            if (counter == null) {
                counter = new MutableInt();
                components.put(lbl, counter);
            }
            counter.increment();
        }
        for (Map.Entry<Integer, MutableInt> size : components.entrySet()) {
            System.out.println("Component " + size.getKey() + " size " + size.getValue());
        }
        
        /*System.out.print("Expanded vertices:");
        int count = outSize.getBuffer().get(0);
        for (int i = 0; i < count; i++) {
            System.out.print(" " + vertices.getBuffer().get(i));
        }
        System.out.println();*/
        
        /*System.out.print("Visited vertices:");
        for (int v : visitedVertices(labels)) {
            System.out.print(" " + v);
        }
        System.out.println();*/
        
        //Set<Integer> reference = expand(graph, 0, 2);
        //Set<Integer> reference = visit(graph, 0, 50);
        //boolean ok = equalsFrontier(vertices, count, reference);
        /*boolean ok = equalsVisited(labels, reference);
        if (!ok) {
            System.out.print("Not matching. Reference (" + reference.size() + "):");
            for (int v : reference) {
                System.out.print(" " + v);
            }
            System.out.println();
        } else {
            //System.out.println("Match.");
        }*/
        
        /*long nttoTime = 0;
        for (int e = 0; e < iter; e++) {
            long start = events.getEvent(e).getProfilingInfo(CLEvent.ProfilingCommand.START);
            long end = events.getEvent(e).getProfilingInfo(CLEvent.ProfilingCommand.END);
            nttoTime += end - start;
        }
        
        long start = events.getEvent(0).getProfilingInfo(CLEvent.ProfilingCommand.START);
        long end = events.getEvent(iter - 1).getProfilingInfo(CLEvent.ProfilingCommand.END);
        long bttoTime = end - start;
        
        if (writePerformanceInfo) {
            writePerformanceInfo = false;
            System.out.println("Time elapsed (OpenCL: bfs - btto): " + (bttoTime / 1000000.0) + " ms");
            System.out.println("Time elapsed (OpenCL: bfs - ntto): " + (nttoTime / 1000000.0) + " ms");
        }*/
    }
    
    private static List<List<Integer>> adjacencyMatrix(edu.princeton.cs.algs4.Graph graph) {
        List<List<Integer>> matrix = new ArrayList<>();
        for (int v = 0; v < graph.V(); v++) {
            List<Integer> row = new ArrayList<>();
            for (int w : graph.adj(v)) {
                row.add(w);
            }
            matrix.add(row);
        }
        return matrix;
    }
    
    private static List<List<Integer>> generateGraph(int ... components) {
        List<List<Integer>> graph = new ArrayList<>();
        int offset = 0;
        for (int vertices : components) {
            List<List<Integer>> component = adjacencyMatrix(GraphGenerator.regular(vertices, 3));
            for (List<Integer> row : component) {
                for (int v = 0; v < 3; v++) {
                    int vertex = row.get(v);
                    row.set(v, offset + vertex);
                }
                graph.add(row);
            }
            offset += component.size();
        }
        return graph;
    }
    
    private boolean equalsFrontier(CLBuffer<IntBuffer> result, int size, Set<Integer> reference) {
        int[] resultData = new int[size];
        result.getBuffer().get(resultData);
        
        Set<Integer> resultSet = new HashSet<>();
        for (int v : resultData) {
            resultSet.add(v);
        }
        
        return resultSet.equals(reference);
    }
    
    private Set<Integer> visitedVertices(CLBuffer<IntBuffer> labels) {
        IntBuffer labelsData = labels.getBuffer();
        Set<Integer> visitedSet = new HashSet<>();
        
        for (int i = 0; i < labelsData.capacity(); i++) {
            if (labelsData.get(i) > 0) {
                visitedSet.add(i);
            }
        }
        
        return visitedSet;
    }
    
    private boolean equalsVisited(CLBuffer<IntBuffer> result, Set<Integer> reference) {
        Set<Integer> labelsSet = visitedVertices(result);
        return labelsSet.equals(reference);
    }
    
    private Set<Integer> expand(edu.princeton.cs.algs4.Graph graph, int start, int iterations) {
        Set<Integer> in = new HashSet<>();
        Set<Integer> out = new HashSet<>();
        
        in.add(start);
        for (int i = 0; i < iterations; i++) {
            out.clear();
            for (int v : in) {
                for (int j : graph.adj(v)) {
                    out.add(j);
                }
            }
            in.clear();
            in.addAll(out);
        }
        
        return out;
    }
    
    private Set<Integer> visit(edu.princeton.cs.algs4.Graph graph, int start, int iterations) {
        Set<Integer> in = new HashSet<>();
        Set<Integer> out = new HashSet<>();
        Set<Integer> visited = new HashSet<>();
        
        visited.add(start);
        in.add(start);
        for (int i = 0; i < iterations; i++) {
            out.clear();
            for (int v : in) {
                for (int j : graph.adj(v)) {
                    out.add(j);
                }
            }
            in.clear();
            in.addAll(out);
            visited.addAll(out);
        }
        
        return visited;
    }
    
    private static List<List<Integer>> readGraph(String filename) throws IOException {
        List<List<Integer>> adjacency = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line = reader.readLine();
            int v = 0;
            while (line != null) {
                String[] vertices = line.trim().split("\\s+");
                List<Integer> adj = new ArrayList<>();
                for (String vert : vertices) {
                    int w = Integer.parseInt(vert);
                    /*if (v < w) {
                        adj.add(w);
                    } else if (v == w && adj.contains(w) == false) {
                        adj.add(w);
                    }*/
                    adj.add(w);
                }
                adjacency.add(adj);
                v++;
                line = reader.readLine();
            }
        }
        return adjacency;
    }
    
    private static void writeGraph(List<List<Integer>> matrix) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("graph.txt"))) {
            for (List<Integer> row : matrix) {
                for (int w : row) {
                    writer.write(String.format("%4d", w));
                }
                writer.newLine();
            }
        }
    }
    
    private static void writeFrontier(IntBuffer frontier, int size, String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (int i = 0; i < Math.min(size, MAX_FRONTIER_VERTICES); i++) {
                writer.write(String.format("%4d", frontier.get(i)));
                writer.newLine();
            }
        }
    }
    
    private static void writeLabels(IntBuffer labels, int size, String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (int i = 0; i < size; i++) {
                int label = labels.get(i);
                writer.write(String.format("%4d", label));
                writer.newLine();
                if (label == 2) {
                    throw new IOException("2"); // DEBUG
                }
            }
        }
    }
    
    private static int roundUp(int groupSize, int globalSize) {
        int r = globalSize % groupSize;
        if (r == 0) {
            return globalSize;
        } else {
            return globalSize + groupSize - r;
        }
    }
    
}
