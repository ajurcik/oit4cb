package csdemo;

import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.GL4;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class Graph {
    
    public void connectedComponents(GL4 gl, int toriBuffer, int edgesBuffer, int torusCount,
            int verticesBuffer, int vertexCount, int sphereCount) {
        boolean[][] adjacency = new boolean[vertexCount][vertexCount];
        int[][] edges = new int[torusCount][2];
        int[][] spheres = new int[torusCount][2];
        
        // map surface edges buffer
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, edgesBuffer);
        IntBuffer edgesData = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asIntBuffer();
        // fill adjacency matrix
        for (int i = 0; i < torusCount; i++) {
            int v0 = edgesData.get(4 * i);
            int v1 = edgesData.get(4 * i + 1);
            if (v0 != -1 && v1 != -1) {
                adjacency[v0][v1] = true;
                adjacency[v1][v0] = true;
                // store edges
                edges[i][0] = v0;
                edges[i][1] = v1;
                // get adjacent spheres
                spheres[i][0] = edgesData.get(4 * i + 2);
                spheres[i][1] = edgesData.get(4 * i + 3);
            }
        }
        // unmap edges buffer
        gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        
        // create CSR representation of the adjacency matrix
        int count = 0;
        int[] rows = new int[vertexCount + 1];
        rows[0] = count;
        for (int i = 0; i < vertexCount; i++) {
            for (int j = 0; j < vertexCount; j++) {
                if (adjacency[i][j]) {
                    count++;
                }
            }
            rows[i + 1] = count;
        }
        
        int index = 0;
        int[] columns = new int[count];
        for (int i = 0; i < vertexCount; i++) {
            for (int j = 0; j < vertexCount; j++) {
                if (adjacency[i][j]) {
                    columns[index] = j;
                    index++;
                }
            }
        }
        
        Deque<Integer> vertices = new ArrayDeque<>();
        List<Integer> sizes = new ArrayList<>();
        boolean[] visited = new boolean[vertexCount];
        int[] labels = new int[vertexCount];
        
        int label = 1;
        int startVertex;
        do {
            // find start vertex
            startVertex = -1;
            for (int i = 0; i < vertexCount; i++) {
                if (!visited[i]) {
                    startVertex = i;
                    break;
                }
            }
            if (startVertex < 0) {
                break;
            }
            // label one connected component using BFS
            int size = 0;
            vertices.add(startVertex);
            while (!vertices.isEmpty()) {
                int vertex = vertices.poll();
                if (visited[vertex]) {
                    continue;
                }
                visited[vertex] = true;
                labels[vertex] = label;
                int start = rows[vertex];
                int end = rows[vertex + 1];
                for (int i = start; i < end; i++) {
                    vertices.add(columns[i]);
                }
                size++;
            }
            sizes.add(size);
            label++;
        } while (startVertex >= 0);
        
        // find largest connceted component
        int largest = 0;
        for (int i = 1; i < sizes.size(); i++) {
            if (sizes.get(i) > sizes.get(largest)) {
                largest = i;
            }
        }
        
        // map surface vertices buffer
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, verticesBuffer);
        IntBuffer verticesBuf = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_WRITE_ONLY).asIntBuffer();
        // write surface/cavity flags
        for (int i = 0; i < labels.length; i++) {
            int value = labels[i] == (largest + 1) ? 1 : 0;
            verticesBuf.put(i, value);
        }
        // unmap edges buffer
        gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
        
        sphereCircles(gl, edges, spheres, sphereCount, toriBuffer, torusCount);
        
        try {
            writeCSR(rows, columns);
            writeLabels(labels, sizes);
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }
    }
    
    private void sphereCircles(GL4 gl, int[][] edges, int[][] spheres, int sphereCount, int toriBuffer, int torusCount) {
        List<List<Integer>> circles = new ArrayList<>();
        for (int i = 0; i < sphereCount; i++) {
            circles.add(new ArrayList<Integer>());
        }
        
        for (int i = 0; i < torusCount; i++) {
            circles.get(spheres[i][0]).add(i);
            circles.get(spheres[i][1]).add(i);
        }
        
        for (int i = 0; i < sphereCount; i++) {
            List<Integer> circle = circles.get(i);
            if (circle.isEmpty()) {
                continue;
            }
            int current = edges[circle.get(0)][1];
            for (int j = 0; j < circle.size() - 1; j++) {
                int k;
                for (k = j + 1; k < circle.size(); k++) {
                    int v0 = edges[circle.get(k)][0];
                    int v1 = edges[circle.get(k)][1];
                    if (current == v0) {
                        current = v1;
                        break;
                    } else if (current == v1) {
                        current = v0;
                        break;
                    }
                }
                if (k == circle.size()) {
                    current = edges[circle.get(j + 1)][1];
                    System.out.println("Another circle found. Sphere: " + i);
                }
                if (k < circle.size() && k != j + 1) {
                    int tmp = circle.get(j + 1);
                    circle.set(j + 1, circle.get(k));
                    circle.set(k, tmp);
                }
            }
        }
        
        // map surface edges buffer
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, toriBuffer);
        FloatBuffer tori = gl.glMapBuffer(GL_SHADER_STORAGE_BUFFER, GL_READ_ONLY).asFloatBuffer();
        // fill adjacency matrix
        /*for (int i = 0; i < torusCount; i++) {
            int v0 = edges.get(4 * i);
            int v1 = edges.get(4 * i + 1);
            if (v0 != -1 && v1 != -1) {
                adjacency[v0][v1] = true;
                adjacency[v1][v0] = true;
                // get adjacent spheres
                spheres[i][0] = edges.get(4 * i + 2);
                spheres[i][1] = edges.get(4 * i + 3);
            }
        }
        // unmap edges buffer*/
        gl.glUnmapBuffer(GL_SHADER_STORAGE_BUFFER);
    }
    
    private void writeCSR(int[] rows, int[] columns) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("CSR.txt"))) {
            writer.append("R: ");
            for (int i = 0; i < rows.length; i++) {
                writer.append(String.format("%4d ", rows[i]));
            }
            writer.newLine();
            writer.append("C: ");
            for (int i = 0; i < columns.length; i++) {
                writer.append(String.format("%4d ", columns[i]));
            }
            writer.newLine();
            writer.newLine();
            // write graph neighbors
            for (int i = 0; i < rows.length - 1; i++) {
                int start = rows[i];
                int end = rows[i + 1];
                writer.append(String.format("%4d: ", i));
                for (int j = start; j < end; j++) {
                    writer.append(String.format("%4d ", columns[j]));
                }
                writer.newLine();
            }
        }
    }
    
    private void writeLabels(int[] labels, List<Integer> sizes) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("labels.txt"))) {
            for (int label = 0; label < sizes.size(); label++) {
                writer.append(String.format("%2d: %4d", label + 1, sizes.get(label)));
                writer.newLine();
            }
            writer.newLine();
            for (int i = 0; i < labels.length; i++) {
                writer.append(String.format("(%4d): %2d", i, labels[i]));
                writer.newLine();
            }
        }
    }
    
    private void writeLabels(int[] labels) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("labels.txt"))) {
            for (int i = 0; i < labels.length; i++) {
                writer.append(String.format("(%4d): %2d", i, labels[i]));
                writer.newLine();
            }
        }
    }
    
}
