package csdemo;

/**
 *
 * @author Adam
 */
public class Mesh {
    
    private int triangleCount;
    private int vertexArrayBuffer;

    public Mesh(int triangleCount, int vertexArrayBuffer) {
        this.triangleCount = triangleCount;
        this.vertexArrayBuffer = vertexArrayBuffer;
    }
    
    public int getTriangleCount() {
        return triangleCount;
    }

    public int getVertexArrayBuffer() {
        return vertexArrayBuffer;
    }
    
}
