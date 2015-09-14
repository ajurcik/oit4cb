package csdemo;

import com.jogamp.opengl.GL4bc;
import static com.jogamp.opengl.GL4bc.*;
import java.io.IOException;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

/**
 *
 * @author Adam Jurčík <xjurc@fi.muni.cz>
 */
public class Polygon {
    
    private int defaultProgram;
    private int polygonProgram;
    
    public void init(GL4bc gl, int fragmentsBufferIndex, int fragmentsIndexBufferIndex) {
        // loading resources (shaders, data)
        try {
            defaultProgram = Utils.loadProgram(gl, "/resources/shaders/default.vert",
                    "/resources/shaders/default.frag");
            polygonProgram = Utils.loadProgram(gl, "/resources/shaders/ray/polygon.vert",
                    "/resources/shaders/ray/polygon.frag");
        } catch (IOException e) {
            System.err.println("Resource loading failed. " + e.getMessage());
            System.exit(1);
        }
        
        Utils.bindShaderStorageBlock(gl, defaultProgram, "ABuffer", fragmentsBufferIndex);
        Utils.bindShaderStorageBlock(gl, defaultProgram, "ABufferIndex", fragmentsIndexBufferIndex);
        
        Utils.bindShaderStorageBlock(gl, polygonProgram, "ABuffer", fragmentsBufferIndex);
        Utils.bindShaderStorageBlock(gl, polygonProgram, "ABufferIndex", fragmentsIndexBufferIndex);
    }
    
    public void display(GL4bc gl, Point3f eye, Point3f center) {
        gl.glUseProgram(polygonProgram);
        
        Vector3f view = new Vector3f();
        Vector3f up = new Vector3f(0f, 1f, 0f);
        Vector3f right = new Vector3f();
        view.sub(center, eye);
        view.normalize();
        right.cross(up, view);
        right.normalize();
        up.cross(view, right);
        Utils.setUniform(gl, polygonProgram, "camIn", view.x, view.y, view.z);
        Utils.setUniform(gl, polygonProgram, "camUp", up.x, up.y, up.z);
        Utils.setUniform(gl, polygonProgram, "camRight", right.x, right.y, right.z);
        
        int[] viewport = new int[4];
        gl.glGetIntegerv(GL_VIEWPORT, viewport, 0);
        Utils.setUniform(gl, polygonProgram, "viewport", 0f, 0f, 2f / viewport[2], 2f / viewport[3]);
        Utils.setUniform(gl, polygonProgram, "window", viewport[2], viewport[3]);
        
        Point3f a = new Point3f(-0.5f, 1f / (float) Math.sqrt(2.0), 0.5f);
        Point3f b = new Point3f(0.5f, 1f / (float) Math.sqrt(2.0), 0.5f);
        Point3f c = new Point3f(0.5f, 1f / (float) Math.sqrt(2.0), -0.5f);
        Point3f d = new Point3f(-0.5f, 1f / (float) Math.sqrt(2.0), -0.5f);
        Point3f e = new Point3f(-0.25f, (float) Math.sqrt(15.0) / 4f, 0f);
        setArcUniforms(gl, 1, a, b);
        setArcUniforms(gl, 2, b, c);
        setArcUniforms(gl, 3, c, d);
        setArcUniforms(gl, 4, d, e);
        setArcUniforms(gl, 5, e, a);
        Utils.setUniform(gl, polygonProgram, "outside", 0f, -1f, 0f);
        
        gl.glBegin(GL_POINTS);
        gl.glColor4f(1f, 0f, 1f, 0.5f);
        gl.glVertex4f(0f, 0f, 0f, 1f);
        gl.glEnd();
    }
    
    private void setArcUniforms(GL4bc gl, int index, Point3f a, Point3f b) {
        // y axis lies by definition in circle plane
        Point3f c = new Point3f(a);
        c.y += 1f;
        
        Vector3f v1 = new Vector3f();
        Vector3f v2 = new Vector3f();
        Vector3f n = new Vector3f();
        v1.sub(b, a);
        v1.normalize();
        v2.sub(c, a);
        v2.normalize();
        n.cross(v1, v2);
        n.normalize();
        
        float d = -(n.x * a.x + n.y * a.y + n.z * a.z);
        if (d > 0) {
            n.scale(-1f);
            d = -d;
        }
        
        Point3f center = new Point3f();
        center.scale(Math.abs(d), n);
        float radius = (float) Math.sqrt(1.0 - d * d);
        
        Vector3f va = new Vector3f();
        Vector3f vb = new Vector3f();
        Vector3f up = new Vector3f();
        va.sub(a, center);
        vb.sub(b, center);
        up.add(va, vb);
        up.normalize();
        up.scale(radius);
        float angle = (float) Math.acos(va.dot(vb) / va.lengthSquared());
        
        /*gl.glUseProgram(defaultProgram);
        
        int[] viewport = new int[4];
        gl.glGetIntegerv(GL_VIEWPORT, viewport, 0);
        Utils.setUniform(gl, defaultProgram, "window", viewport[2], viewport[3]);
        
        Vector3f y = new Vector3f(center);
        y.normalize();
        Vector3f z = new Vector3f();
        z.cross(y, new Vector3f(1f, 1f, 0f));
        z.normalize();
        Vector3f x = new Vector3f();
        x.cross(y, z);
        
        float[] planeRot = new float[] {
            x.x, x.y, x.z, 0f,
            y.x, y.y, y.z, 0f,
            z.x, z.y, z.z, 0f,
            0f, 0f, 0f, 1f,
        };
        
        gl.glPushMatrix();
        gl.glTranslatef(center.x, center.y, center.z);
        gl.glMultMatrixf(planeRot, 0);
        Utils.drawPlane(gl, 1.5f, new Vector4f(1f, 1f, 0f, 0.8f));
        gl.glPopMatrix();
        
        gl.glUseProgram(polygonProgram);*/
        
        Utils.setUniform(gl, polygonProgram, "circle" + index, center.x, center.y, center.z, radius);
        Utils.setUniform(gl, polygonProgram, "arc" + index, up.x, up.y, up.z, angle);
    }
    
}
