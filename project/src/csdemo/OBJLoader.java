package csdemo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class OBJLoader {

    private String path;
    
    private List<float[]> vertices;
    private List<float[]> normals;
    private List<float[]> texcoords;
    private List<int[]> vertexIndices;
    private List<int[]> normalIndices;
    private List<int[]> texcoordIndices;
    
    private BufferedReader inReader;

    public OBJLoader(String path) {
        this.path = path;
    }

    public void load() {
        /** Mesh containing the loaded object */
        vertices = new ArrayList<>();
        normals = new ArrayList<>();
        texcoords = new ArrayList<>();
        vertexIndices = new ArrayList<>();
        normalIndices = new ArrayList<>();
        texcoordIndices = new ArrayList<>();
        
        String line;
        try {
            inReader = new BufferedReader(new InputStreamReader(
                    this.getClass().getResource(path).openStream()));
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            return;
        }
        try {
            while ((line = inReader.readLine()) != null) {
                
                if (line.startsWith("v ")) {
                    
                    String[] vertStr = line.split("\\s+");
                    float[] vertex = new float[3];
                            
                    vertex[0] = Float.parseFloat(vertStr[1]);
                    vertex[1] = Float.parseFloat(vertStr[2]);
                    vertex[2] = Float.parseFloat(vertStr[3]);
                    vertices.add(vertex);
                    
                } else if (line.startsWith("vn ")) {
                    
                    String[] normStr = line.split("\\s+");
                    float[] normal = new float[3];
                    
                    normal[0] = Float.parseFloat(normStr[1]);
                    normal[1] = Float.parseFloat(normStr[2]);
                    normal[2] = Float.parseFloat(normStr[3]);
                    normals.add(normal);
                    
                } else if (line.startsWith("vt ")) {
                    
                    String[] texcoordStr = line.split("\\s+");
                    float[] texcoord = new float[2];
                    
                    texcoord[0] = Float.parseFloat(texcoordStr[1]);
                    texcoord[1] = Float.parseFloat(texcoordStr[2]);
                    texcoords.add(texcoord);
                    
                } else if (line.startsWith("f ")) {
                    
                    String[] faceStr = line.split("\\s+");
                    int[] faceVert = new int[3];

                    faceVert[0] = Integer.parseInt(faceStr[1].split("/")[0]) - 1;
                    faceVert[1] = Integer.parseInt(faceStr[2].split("/")[0]) - 1;
                    faceVert[2] = Integer.parseInt(faceStr[3].split("/")[0]) - 1;
                    vertexIndices.add(faceVert);
                    
                    //int[] faceTexcoord = new int[3];
                    //faceTexcoord[0] = Integer.parseInt(faceStr[1].split("/")[1]) - 1;
                    //faceTexcoord[1] = Integer.parseInt(faceStr[2].split("/")[1]) - 1;
                    //faceTexcoord[2] = Integer.parseInt(faceStr[3].split("/")[1]) - 1;
                    //texcoordIndices.add(faceTexcoord);
                    
                    // TODO: indexy texturovych suradnic (2. hodnota z trojice cisel)
                    
                    if (faceStr[1].split("/").length >= 3) {
                        int[] faceNorm = new int[3];

                        faceNorm[0] = Integer.parseInt(faceStr[1].split("/")[2]) - 1;
                        faceNorm[1] = Integer.parseInt(faceStr[2].split("/")[2]) - 1;
                        faceNorm[2] = Integer.parseInt(faceStr[3].split("/")[2]) - 1;
                        normalIndices.add(faceNorm);
                    }
                }
            }
        } catch (IOException ex) {
            System.out.println("Unable to load " + path + " file: " + ex.getMessage());
        }
    }

    public List<float[]> getVertices() {
        return vertices;
    }

    public List<float[]> getNormals() {
        return normals;
    }
    
    public List<float[]> getTexcoords() {
        return texcoords;
    }

    public List<int[]> getVertexIndices() {
        return vertexIndices;
    }

    public List<int[]> getNormalIndices() {
        return normalIndices;
    }
    
    public List<int[]> getTexcoordIndices() {
        return texcoordIndices;
    }
}
