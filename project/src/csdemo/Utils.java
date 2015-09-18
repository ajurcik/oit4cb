package csdemo;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL2;
import static com.jogamp.opengl.GL4bc.*;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

/**
 *
 * @author Adam Jurčík <xjurc@fi.muni.cz>
 */
public class Utils {
    
    public static final int INVALID_LOCATION = -1;
    
    private static final Vector4f TRANSPARENT_YELLOW = new Vector4f(1f, 1f, 0f, 0.3f);
    private static final IntBuffer COUNTER_DATA = Buffers.newDirectIntBuffer(1);
    
    private static final Map<String, Float> radii;
    private static final Map<String, Map<String, Float>> volumes;

    private static final GLUT glut = new GLUT();
    
    static {
        try {
            // load van der Waals radii
            radii = loadVDWRadii("/resources/vdwradii.csv");
            // load volumes
            volumes = loadVolumes("/resources/volumes.csv");
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
            throw new RuntimeException("Could not load vdwradii.csv or volumes.csv from resources", ex);
        }
    }
    
    public static int loadShader(GL4 gl, String filename, int shaderType) throws IOException {
        String source = readFile(Utils.class.getResourceAsStream(filename));
        int shader = gl.glCreateShader(shaderType);
        
        // Create and compile GLSL shader
        gl.glShaderSource(shader, 1, new String[] { source }, new int[] { source.length() }, 0);
        gl.glCompileShader(shader);
        
        // Check GLSL shader compile status
        int[] status = new int[1];
        gl.glGetShaderiv(shader, GL_COMPILE_STATUS, status, 0);
        if (status[0] == GL_FALSE) {
            int[] length = new int[1];
            gl.glGetShaderiv(shader, GL_INFO_LOG_LENGTH, length, 0);
            
            byte[] log = new byte[length[0]];
            gl.glGetShaderInfoLog(shader, length[0], length, 0, log, 0);
            
            String error = new String(log, 0, length[0]);
            System.err.print(filename + ": ");
            System.err.println(error);
        }
        
        return shader;
    }
    
    public static int loadProgram(GL4 gl, String vertexShaderFN, String fragmentShaderFN) throws IOException {
        // Load frament and vertex shaders (GLSL)
	int vs = loadShader(gl, vertexShaderFN, GL_VERTEX_SHADER);
	int fs = loadShader(gl, fragmentShaderFN, GL_FRAGMENT_SHADER);
        
	// Create GLSL program, attach shaders and compile it
	int program = gl.glCreateProgram();
	gl.glAttachShader(program, vs);
	gl.glAttachShader(program, fs);
	gl.glLinkProgram(program);
        
        int[] linkStatus = new int[1];
        gl.glGetProgramiv(program, GL_LINK_STATUS, linkStatus, 0);

        if (linkStatus[0] == GL_FALSE) {
            int[] length = new int[1];
            gl.glGetProgramiv(program, GL_INFO_LOG_LENGTH, length, 0);
            
            byte[] log = new byte[length[0]];
            gl.glGetProgramInfoLog(program, length[0], length, 0, log, 0);
            
            String error = new String(log, 0, length[0]);
            System.err.println(error);
        }
        
        return program;
    }
    
    public static int loadProgram(GL4 gl, String vertexShaderFN, String geometryShaderFN, String fragmentShaderFN)
            throws IOException {
        // Load frament and vertex shaders (GLSL)
	int vs = loadShader(gl, vertexShaderFN, GL_VERTEX_SHADER);
        int gs = loadShader(gl, geometryShaderFN, GL_GEOMETRY_SHADER);
	int fs = loadShader(gl, fragmentShaderFN, GL_FRAGMENT_SHADER);
        
	// Create GLSL program, attach shaders and compile it
	int program = gl.glCreateProgram();
	gl.glAttachShader(program, vs);
        gl.glAttachShader(program, gs);
	gl.glAttachShader(program, fs);
	gl.glLinkProgram(program);
        
        int[] linkStatus = new int[1];
        gl.glGetProgramiv(program, GL_LINK_STATUS, linkStatus, 0);

        if (linkStatus[0] == GL_FALSE) {
            int[] length = new int[1];
            gl.glGetProgramiv(program, GL_INFO_LOG_LENGTH, length, 0);
            
            byte[] log = new byte[length[0]];
            gl.glGetProgramInfoLog(program, length[0], length, 0, log, 0);
            
            String error = new String(log, 0, length[0]);
            System.err.println(error);
        }
        
        return program;
    }
    
    public static int loadComputeProgram(GL4 gl, String computeShaderFN) throws IOException {
        // Load compute shader (GLSL)
	int cs = loadShader(gl, computeShaderFN, GL_COMPUTE_SHADER);
        
	// Create GLSL program, attach shaders and compile it
	int program = gl.glCreateProgram();
	gl.glAttachShader(program, cs);
	gl.glLinkProgram(program);
        
        int[] linkStatus = new int[1];
        gl.glGetProgramiv(program, GL_LINK_STATUS, linkStatus, 0);

        if (linkStatus[0] == GL_FALSE) {
            int[] length = new int[1];
            gl.glGetProgramiv(program, GL_INFO_LOG_LENGTH, length, 0);
            
            byte[] log = new byte[length[0]];
            gl.glGetProgramInfoLog(program, length[0], length, 0, log, 0);
            
            String error = new String(log, 0, length[0]);
            System.err.print(computeShaderFN + ": ");
            System.err.println(error);
        }
        
        return program;
    }
    
    public static String readFile(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        
        int c;
        while ((c = reader.read()) != -1) {
            sb.append((char) c);
        }
        
        return sb.toString();
    }
    
    public static List<Atom> loadAtoms(File file) throws IOException {
        return loadAtoms(new FileInputStream(file));
    }
    
    public static List<Atom> loadAtomsFromResource(String name) throws IOException {
        InputStream is = Utils.class.getResourceAsStream(name);
        return loadAtoms(is);
    }
    
    private static List<Atom> loadAtoms(InputStream is) throws IOException {
        // load atom coordinates
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is)))
        {
            List<Atom> atoms = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("ATOM")) {
                    String name = line.substring(12, 16).trim();
                    String residue = line.substring(17, 20).trim();
                    Atom atom = new Atom();
                    atom.x = Float.parseFloat(line.substring(30, 38));
                    atom.y = Float.parseFloat(line.substring(38, 46));
                    atom.z = Float.parseFloat(line.substring(46, 54));
                    String code = line.substring(76, 78).trim();
                    if (code == null || code.isEmpty()) {
                        code = name.substring(0, 1);
                    }
                    atom.r = radii.get(code);
                    if (residue.equals("HIE") || residue.equals("HID") || residue.equals("HIP")) {
                        residue = "HIS";
                    }
                    Float v = volumes.get(residue).get(name);
                    if (v == null) {
                        v = 0f;
                    }
                    atom.v = v;
                    atoms.add(atom);
                }
            }
            return atoms;
        }
    }
    
    public static float getAtomRadius(String code) {
        return radii.get(code);
    }
    
    public static float getAtomVolume(String residue, String name) {
        Float v = volumes.get(residue).get(name);
        if (v == null) {
            System.err.println("Warning: volume for " + residue + ", " + name + " could not be determined");
            v = 0f;
        }
        return v;
    }
    
    public static List<List<Atom>> loadDynamics(File[] files) throws IOException {
        int atomCount = 0;
        List<List<Atom>> dynamics = new ArrayList<>();
        for (File file : files) {
            List<Atom> snapshot = loadAtoms(file);
            dynamics.add(snapshot);
            // check atom count among snapshots
            if (atomCount == 0) {
                atomCount = snapshot.size();
            } else if (atomCount != snapshot.size()) {
                throw new IllegalArgumentException("Shapshots have different atom count");
            }
        }
        return dynamics;
    }
    
    public static List<List<Atom>> loadDynamicsFromResource(String name, int start, int end)
            throws IOException {
        List<List<Atom>> dynamics = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            List<Atom> snapshot = loadAtomsFromResource(name + "." + i + ".pdb");
            dynamics.add(snapshot);
        }
        return dynamics;
    }
    
    public static Map<String, Float> loadVDWRadii(String name) throws IOException {
        InputStream is = Utils.class.getResourceAsStream(name);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            Map<String, Float> radii = new HashMap<>();
            String line;
            // read out the header
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                // every line has 4 columns
                String[] columns = line.split(";");
                try {
                    float r = Float.parseFloat(columns[3]) / 100f;
                    radii.put(columns[1], r);
                } catch (NumberFormatException e) {
                    // there is no radii data
                }
            }
            return radii;
        }
    }
    
    public static Map<String, Map<String, Float>> loadVolumes(String name) throws IOException {
        InputStream is = Utils.class.getResourceAsStream(name);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            Map<String, Map<String, Float>> volumes = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                // every line has 3 columns
                String[] columns = line.split(";");
                try {
                    Map<String, Float> residue = volumes.get(columns[0]);
                    if (residue == null) {
                        residue = new HashMap<>();
                        volumes.put(columns[0], residue);
                    }
                    float v = Float.parseFloat(columns[2].replace(',', '.'));
                    residue.put(columns[1], v);
                } catch (NumberFormatException e) {
                    // should not happen
                }
            }
            return volumes;
        }
    }
        
    public static void bindShaderStorageBlock(GL4 gl, int program, String name, int index) {
        int blockIndex = gl.glGetProgramResourceIndex(program, GL_SHADER_STORAGE_BLOCK, name.getBytes(), 0);
        if (blockIndex != GL_INVALID_INDEX) {
            gl.glShaderStorageBlockBinding(program, blockIndex, index);
        } else {
            System.err.println("Warning: binding " + name + " not found");
        }
    }
    
    public static void bindUniformBlock(GL4 gl, int program, String name, int index) {
        int blockIndex = gl.glGetUniformBlockIndex(program, name);
        //int blockIndex = gl.glGetProgramResourceIndex(program, GL_UNIFORM_BLOCK, name.getBytes(), 0);
        if (blockIndex != GL_INVALID_INDEX) {
            gl.glUniformBlockBinding(program, blockIndex, index);
        } else {
            System.err.println("Warning: binding " + name + " not found");
        }
    }
    
    public static int getCounter(GL4 gl, int buffer) {
        return getCounter(gl, buffer, 0);
    }
    
    public static int getCounter(GL4 gl, int buffer, int offset) {
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
        gl.glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, 4, COUNTER_DATA);
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        return COUNTER_DATA.get(0);
    }
    
    public static void clearCounter(GL4 gl, int buffer) {
        setCounter(gl, buffer, 0, 0);
    }
    
    public static void clearCounter(GL4 gl, int buffer, int offset) {
        setCounter(gl, buffer, offset, 0);
    }
    
    public static void setCounter(GL4 gl, int buffer, int offset, int value) {
        COUNTER_DATA.put(value);
        COUNTER_DATA.rewind();
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
        gl.glBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, 4, COUNTER_DATA);
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }
    
    public static void setSampler(GL4 gl, int program, String name, int unit) {
        int location = gl.glGetUniformLocation(program, name);
        if (location == INVALID_LOCATION) {
            System.err.println("Warning: sampler " + name + " not found");
        } else {
            gl.glUniform1i(location, unit);
        }
    }
    
    public static void setUniform(GL4 gl, int program, String name, int value) {
        int location = gl.glGetUniformLocation(program, name);
        if (location == INVALID_LOCATION) {
            System.err.println("Warning: uniform " + name + " not found");
        } else {
            gl.glUniform1ui(location, value);
        }
    }
    
    public static void setUniform(GL4 gl, int program, String name, int x, int y) {
        int location = gl.glGetUniformLocation(program, name);
        if (location == INVALID_LOCATION) {
            System.err.println("Warning: uniform " + name + " not found");
        } else {
            gl.glUniform2ui(location, x, y);
        }
    }
    
    public static void setUniform(GL4 gl, int program, String name, float value) {
        int location = gl.glGetUniformLocation(program, name);
        if (location == INVALID_LOCATION) {
            System.err.println("Warning: uniform " + name + " not found");
        } else {
            gl.glUniform1f(location, value);
        }
    }
    
    public static void setUniform(GL4 gl, int program, String name, float x, float y, float z) {
        int location = gl.glGetUniformLocation(program, name);
        if (location == INVALID_LOCATION) {
            System.err.println("Warning: uniform " + name + " not found");
        } else {
            gl.glUniform3f(location, x, y, z);
        }
    }
    
    public static void setUniform(GL4 gl, int program, String name, float x, float y, float z, float w) {
        int location = gl.glGetUniformLocation(program, name);
        if (location == INVALID_LOCATION) {
            System.err.println("Warning: uniform " + name + " not found");
        } else {
            gl.glUniform4f(location, x, y, z, w);
        }
    }
    
    public static void setUniform(GL4 gl, int program, String name, boolean value) {
        setUniform(gl, program, name, value ? 1 : 0);
    }
    
    public static void setUniform(GL4 gl, int program, String name, Color color) {
        setUniform(gl, program, name, color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f);
    }
    
    public static void drawAxes(GL2 gl, float size) {
        gl.glBegin(GL2.GL_LINES);
        // X
        gl.glColor3f(1f, 0f, 0f);
        gl.glVertex3f(0f, 0f, 0f);
        gl.glVertex3f(size, 0f, 0f);
        // Y
        gl.glColor3f(0f, 1f, 0f);
        gl.glVertex3f(0f, 0f, 0f);
        gl.glVertex3f(0f, size, 0f);
        // Z
        gl.glColor3f(0f, 0f, 1f);
        gl.glVertex3f(0f, 0f, 0f);
        gl.glVertex3f(0f, 0f, size);
        
        gl.glEnd();
    }
    
    public static void drawAABB(GL2 gl, Point3f min, float size) {
        gl.glColor3f(0f, 1f, 0f);
        gl.glPushMatrix();
        gl.glTranslatef(min.x + 0.5f * size, min.y + 0.5f * size, min.z + 0.5f * size);
        glut.glutWireCube(size);
        gl.glPopMatrix();
    }
    
    public static void drawPlane(GL2 gl, float size) {
        drawPlane(gl, size, TRANSPARENT_YELLOW);
    }
    
    public static void drawPlane(GL2 gl, float size, Vector4f color) {
        gl.glBegin(GL_QUADS);
        gl.glColor4f(color.x, color.y, color.z, color.w);
        gl.glVertex3f(-size, 0f, -size);
        gl.glVertex3f(size, 0f, -size);
        gl.glVertex3f(size, 0f, size);
        gl.glVertex3f(-size, 0f, size);
        gl.glEnd();
    }
    
    public static void drawPlane(GL2 gl, Vector4f plane, float size) {
        gl.glPushAttrib(GL_ALL_ATTRIB_BITS);
        gl.glLineWidth(2.0f);
        
        Vector3f n = new Vector3f(plane.x, plane.y, plane.z);
        Vector3f z = new Vector3f();
        z.cross(n, new Vector3f(1f, 0f, 0f));
        z.normalize();
        Vector3f x = new Vector3f();
        x.cross(n, z);
        Vector3f t = new Vector3f(plane.x, plane.y, plane.z);
        t.scale(-plane.w);
        
        gl.glPushMatrix();
        
        float[] rotMat = new float[] {
            x.x, x.y, x.z, 0f,
            n.x, n.y, n.z, 0f,
            z.x, z.y, z.z, 0f,
            0f, 0f, 0f, 1f,
        };
        
        gl.glTranslatef(t.x, t.y, t.z);
        gl.glMultMatrixf(rotMat, 0);
        drawAxes(gl, 2);
        drawPlane(gl, size, new Vector4f(1f, 1f, 0f, 0.7f));
        
        gl.glPopMatrix();
        gl.glPopAttrib();
    }
    
    public static void drawPoint(GL2 gl, Vector3f point, float size) {
        gl.glPushMatrix();
        gl.glTranslatef(point.x, point.y, point.z);
        
        drawAxes(gl, size);
        
        gl.glPopMatrix();
    }
    
}
