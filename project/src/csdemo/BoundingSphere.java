package csdemo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class BoundingSphere {
    
    private static final float HALF_PI = (float) (Math.PI / 2.0);
    
    private Vector4f sphere;
    private Vector4f cone;

    public BoundingSphere(Collection<Vector3f> vectors) {
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("vectors is empty");
        }
        
        List<Vector3f> points = normalize(vectors);
        
        // handle special cases
//        if (vectors.size() == 1) {
//            Vector3f p0 = points.get(0);
//            cone = new Vector4f(p0.x, p0.y, p0.z, 1f);
//            return;
//        } else if (vectors.size() == 2) {
//            Vector3f p0 = points.get(0);
//            Vector3f p1 = points.get(1);
//            cone = sphericalCircle(p0, p1);
//            return;
//        }
        
        cone = minCone(points);
        //sphere = minSphere(points);
    }
    
    public Vector4f getCone() {
        return cone;
    }
    
    public Vector4f getSphere() {
        return sphere;
    }
    
    private List<Vector3f> normalize(Collection<Vector3f> vectors) {
        List<Vector3f> normals = new ArrayList<>(vectors.size());
        
        for (Vector3f v : vectors) {
            Vector3f n = new Vector3f(v);
            n.normalize();
            normals.add(n);
        }
        
        return normals;
    }
    
    /**
     * Quick heuristics whether vectors lie in the same hemisphere when
     * projected a on unit sphere.
     * 
     * @param vectors
     * @return 
     */
    private boolean sameHemisphere(List<Vector3f> vectors) {
        Vector3f v0 = vectors.get(0);
        
        for (int i = 1; i < vectors.size(); i++) {
            Vector3f vi = vectors.get(i);
            if (v0.dot(vi) > HALF_PI) {
                return false;
            }
        }
        
        return true;
    }
    
    private Vector4f minSphere(List<Vector3f> points) {
        Vector4f s = sphere(points.get(0), points.get(1), points.get(2));
        for (int i = 3; i < points.size(); i++) {
            Vector3f pi = points.get(i);
            if (!sphereContains(s, pi)) {
                s = minSphere(points.subList(0, i), pi);
            }
        }
        return s;
    }
    
    private Vector4f minSphere(List<Vector3f> points, Vector3f q) {
        Vector4f s = sphere(points.get(0), points.get(1), q);
        for (int i = 2; i < points.size(); i++) {
            Vector3f pi = points.get(i);
            if (!sphereContains(s, pi)) {
                s = minSphere(points.subList(0, i), pi, q);
            }
        }
        return s;
    }
    
    private Vector4f minSphere(List<Vector3f> points, Vector3f q1, Vector3f q2) {
        Vector4f s = sphere(points.get(0), q1, q2);
        for (int i = 1; i < points.size(); i++) {
            Vector3f pi = points.get(i);
            if (!sphereContains(s, pi)) {
                s = minSphere(points.subList(0, i), pi, q1, q2);
            }
        }
        return s;
    }
    
    private Vector4f minSphere(List<Vector3f> points, Vector3f q1, Vector3f q2, Vector3f q3) {
        Vector4f s = sphere(q1, q2, q3);
        for (int i = 0; i < points.size(); i++) {
            Vector3f pi = points.get(i);
            if (!sphereContains(s, pi)) {
                s = sphere(q1, q2, q3, pi);
            }
        }
        return s;
    }
    
    /*
     * http://stackoverflow.com/questions/8947151/smallest-sphere-to-encapsulate-a-triangle-in-3d
     */
    public static Vector4f sphere(Vector3f p0, Vector3f p1, Vector3f p2) {
        // check sphere definition
        Vector3f v01 = new Vector3f();
        v01.sub(p0, p1);
        if (v01.length() < 0.01f) {
            // coincidence
            return new Vector4f(p0.x, p0.y, p0.z, 0.0f);
        } else {
            //float d = length(cross(p0 - p1, p0 - p2)) / length(p2 - p1);
            Vector3f v02 = new Vector3f();
            Vector3f v21 = new Vector3f();
            v02.sub(p0, p2);
            v21.sub(p2, p1);
            Vector3f w = new Vector3f();
            w.cross(v01, v02);
            float d = w.length() / v21.length();
            if (d < 0.01f) {
                // colinearity
                float A = v01.length();
                float B = v21.length();
                float C = v02.length();
                Vector4f s = new Vector4f();
                if (A > B) {
                    if (A > C) {
                        s.x = (p0.x + p1.x) / 2.0f;
                        s.y = (p0.y + p1.y) / 2.0f;
                        s.z = (p0.z + p1.z) / 2.0f;
                        s.w = A / 2.0f;
                    } else {
                        s.x = (p2.x + p0.x) / 2.0f;
                        s.y = (p2.y + p0.y) / 2.0f;
                        s.z = (p2.z + p0.z) / 2.0f;
                        s.w = C / 2.0f;
                    }
                } else {
                    if (B > C) {
                        s.x = (p1.x + p2.x) / 2.0f;
                        s.y = (p1.y + p2.y) / 2.0f;
                        s.z = (p1.z + p2.z) / 2.0f;
                        s.w = B / 2.0f;
                    } else {
                        s.x = (p2.x + p0.x) / 2.0f;
                        s.y = (p2.y + p0.y) / 2.0f;
                        s.z = (p2.z + p0.z) / 2.0f;
                        s.w = C / 2.0f;
                    }
                }
                return s;
            }
        }

        Vector4f s = new Vector4f();
        // Calculate relative distances
        Vector3f v02 = new Vector3f();
        Vector3f v21 = new Vector3f();
        v02.sub(p0, p2);
        v21.sub(p2, p1);
        float A = v01.length();
        float B = v21.length();
        float C = v02.length();

        // Re-orient triangle (make A longest side)
        Vector3f a = p2, b = p0, c = p1;
        if (B < C) {
            //swap(B, C), swap(b, c);
            float TMP = B; B = C; C = TMP;
            Vector3f tmp = b; b = c; c = tmp;
        }
        if (A < B) {
            //swap(A, B), swap(a, b);
            float TMP = A; A = B; B = TMP;
            Vector3f tmp = a; a = b; b = tmp;
        }

        // If obtuse, just use longest diameter, otherwise circumscribe
        if ((B*B) + (C*C) <= (A*A)) {
            s.w = A / 2.0f;
            s.x = (b.x + c.x) / 2.0f;
            s.y = (b.y + c.y) / 2.0f;
            s.z = (b.z + c.z) / 2.0f;
        } else {
            // http://en.wikipedia.org/wiki/Circumscribed_circle
            float cos_a = (B*B + C*C - A*A) / (B*C*2.0f);
            s.w = A / (float) (Math.sqrt(1 - cos_a*cos_a) * 2.0f);
            //s.xyz = cross(beta * dot(alpha, alpha) - alpha * dot(beta, beta), cross(alpha, beta)) /
            //    (dot(cross(alpha, beta), cross(alpha, beta)) * 2.0) + c;
            Vector3f alpha = new Vector3f();
            Vector3f beta = new Vector3f();
            alpha.sub(a, c);
            beta.sub(b, c);
            Vector3f ba = new Vector3f(beta);
            ba.scale(alpha.dot(alpha));
            Vector3f ab = new Vector3f(alpha);
            ab.scale(beta.dot(beta));
            Vector3f lnom = new Vector3f();
            Vector3f rnom = new Vector3f();
            lnom.sub(ba, ab);
            rnom.cross(alpha, beta);
            Vector3f nom = new Vector3f();
            nom.cross(lnom, rnom);
            ab.cross(alpha, beta);
            float denom = ab.dot(ab) * 2f;
            s.x = nom.x / denom + c.x;
            s.y = nom.y / denom + c.y;
            s.z = nom.z / denom + c.z;
        }
        return s;
    }

    /*
     * http://steve.hollasch.net/cgindex/geometry/sphere4pts.html
     */
    public static Vector4f sphere(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3) {
        // check sphere definition
        Vector3f v01 = new Vector3f();
        v01.sub(p0, p1);
        if (v01.length() < 0.01f) {
            // coincidence
            return new Vector4f(p0.x, p0.y, p0.z, 0.0f);
        } else {
            //float d = length(cross(p0 - p1, p0 - p2)) / length(p2 - p1);
            Vector3f v02 = new Vector3f();
            Vector3f v21 = new Vector3f();
            v02.sub(p0, p2);
            v21.sub(p2, p1);
            Vector3f w = new Vector3f();
            w.cross(v01, v02);
            float d = w.length() / v21.length();
            if (d < 0.01f) {
                // colinearity
                float A = v01.length();
                float B = v21.length();
                float C = v02.length();
                Vector4f s = new Vector4f();
                if (A > B) {
                    if (A > C) {
                        s.x = (p0.x + p1.x) / 2.0f;
                        s.y = (p0.y + p1.y) / 2.0f;
                        s.z = (p0.z + p1.z) / 2.0f;
                        s.w = A / 2.0f;
                    } else {
                        s.x = (p2.x + p0.x) / 2.0f;
                        s.y = (p2.y + p0.y) / 2.0f;
                        s.z = (p2.z + p0.z) / 2.0f;
                        s.w = C / 2.0f;
                    }
                } else {
                    if (B > C) {
                        s.x = (p1.x + p2.x) / 2.0f;
                        s.y = (p1.y + p2.y) / 2.0f;
                        s.z = (p1.z + p2.z) / 2.0f;
                        s.w = B / 2.0f;
                    } else {
                        s.x = (p2.x + p0.x) / 2.0f;
                        s.y = (p2.y + p0.y) / 2.0f;
                        s.z = (p2.z + p0.z) / 2.0f;
                        s.w = C / 2.0f;
                    }
                }
                return s;
            } else {
                Vector4f p = new Vector4f();
                w.cross(v01, v02);
                w.normalize();
                p.x = w.x;
                p.y = w.y;
                p.z = w.z;
                p.w = -(p.x * p0.x + p.y * p0.y + p.z * p0.z);
                if (Math.abs(p.x * p3.x + p.y * p3.y + p.z * p3.z + p.w) < 0.01f) {
                    // coplanarity
                    return sphere(p0, p1, p2);
                }
            }
        }

        Vector4f s0 = sphere(p0, p1, p2);
        // circle plane
        Vector4f cp = new Vector4f();
        Vector3f v10 = new Vector3f();
        Vector3f v20 = new Vector3f();
        v10.sub(p1, p0);
        v20.sub(p2, p0);
        Vector3f w = new Vector3f();
        w.cross(v10, v20);
        w.normalize();
        cp.x = w.x;
        cp.y = w.y;
        cp.z = w.z;
        cp.w = -(cp.x * p0.x + cp.y * p0.y + cp.z * p0.z);

        // compute e
        //float d1 = length(cross(p3 - s0.xyz, p3 - s0.xyz + cp.xyz));
        Vector3f e = new Vector3f();
        Vector3f d1l = new Vector3f(p3.x - s0.x, p3.y - s0.y, p3.z - s0.z);
        Vector3f d1r = new Vector3f(p3.x - s0.x + cp.x, p3.y - s0.y + cp.y, p3.z - s0.z + cp.z);
        Vector3f d1 = new Vector3f();
        d1.cross(d1l, d1r);
        if (d1.length() < 0.001f) {
            // p3 lies on cp.xyz
            e = p0;
        } else {
            // project p3 on cp
            Vector3f p3cp = new Vector3f(cp.x, cp.y, cp.z);
            p3cp.scale(-((p3.x - p0.x) * cp.x + (p3.y - p0.y) * cp.y + (p3.z - p0.z) * cp.z));
            p3cp.add(p3);
            Vector3f subp3cps0 = new Vector3f(p3cp.x - s0.x, p3cp.y - s0.y, p3cp.z - s0.z);
            subp3cps0.normalize();
            e.x = s0.x + s0.w * subp3cps0.x;
            e.y = s0.y + s0.w * subp3cps0.y;
            e.z = s0.z + s0.w * subp3cps0.z;            
        }

        Vector3f ep3 = new Vector3f();
        ep3.add(e, p3);
        ep3.scale(0.5f);
        Vector4f ep3p = new Vector4f();
        Vector3f subp3e = new Vector3f();
        subp3e.sub(p3, e);
        subp3e.normalize();
        ep3p.x = subp3e.x;
        ep3p.y = subp3e.y;
        ep3p.z = subp3e.z;
        ep3p.w = -(ep3p.x * ep3.x + ep3p.y * ep3.y + ep3p.z * ep3.z);

        // intersection of cp.xyz with ep3p
        float d2 = ((ep3.x - s0.x) * ep3p.x + (ep3.y - s0.y) * ep3p.y + (ep3.z - s0.z) * ep3p.z)
                / (cp.x * ep3p.x + cp.y * ep3p.y + cp.z * ep3p.z);

        Vector4f s = new Vector4f();
        s.x = s0.x + d2 * cp.x;
        s.y = s0.y + d2 * cp.y;
        s.z = s0.z + d2 * cp.z;
        Vector3f subsp0 = new Vector3f(s.x - p3.x, s.y - p3.y, s.z - p3.z);
        s.w = subsp0.length();

        Vector3f dp3 = new Vector3f(p3.x - s.x, p3.y - s.y, p3.z - s.z);
        Vector3f de = new Vector3f(e.x - s.x, e.y - s.y, e.z - s.z);
        if (Math.abs(dp3.length() - de.length()) > 0.01f) {
            int brk = 1;
        }
        
        return s;
    }
    
    private static boolean sphereContains(Vector4f s, Vector3f p) {
        Vector3f d = new Vector3f();
        d.x = p.x - s.x;
        d.y = p.y - s.y;
        d.z = p.z - s.z;
        return d.lengthSquared() <= s.w * s.w;
    }
    
    /**
     * Input: A set S of n points on S2.
     * Output: A minimum-radius spherical circle on S2 that fully contains S.
     * 
     * @param points
     * @return 
     */
    private Vector4f minSphericalCircle(List<Vector3f> points) {
        // compute a random permutation p1, . . . ,pn of the points in S.
        // let c2 be the smallest spherical circle enclosing {p1,p2}.
        Vector4f c = sphericalCircle(points.get(0), points.get(1));
        for (int i = 2; i < points.size(); i++) {
            Vector3f pi = points.get(i);
            if (!sphericalCircleContains(c, pi)) {
                c = minSphericalCircleWithPoint(points.subList(0, i), pi);
            }
        }
        return c;
    }
    
    /**
     * Input: A set S of n points on S2, and a point q s.t. there exists an
     * enclosing spherical circle of S that passes through q.
     * Output: The minimum-radius spherical circle on S2 that fully contains
     * S and that passes through q.
     * 
     * @param points
     * @param q
     * @return 
     */
    private Vector4f minSphericalCircleWithPoint(List<Vector3f> points, Vector3f q) {
        // compute a random permutation p1, . . . ,pn of the points in S.
        // let c1 be the smallest spherical circle enclosing {p1,q}.
        Vector4f c = sphericalCircle(points.get(0), q);
        for (int i = 1; i < points.size(); i++) {
            Vector3f pi = points.get(i);
            if (!sphericalCircleContains(c, pi)) {
                c = minSphericalCircleWithTwoPoints(points.subList(0, i), pi, q);
            }
        }
        return c;
    }
     
    /**
     * Input: A set S of n points on S2, and two points q1,q2 s.t. there exists
     * an enclosing spherical circle of S that passes through q1 and q2.
     * Output: The minimum-radius spherical circle on S2 that fully contains S
     * and that passes through q1 and q2.
     * 
     * @param points
     * @param q1
     * @param q2
     * @return 
     */
    private Vector4f minSphericalCircleWithTwoPoints(List<Vector3f> points, Vector3f q1, Vector3f q2) {
        // lompute a random permutation p1, . . . ,pn of the points in S.
        // let c0 be the smallest spherical circle enclosing {q1,q2}.
        Vector4f c = sphericalCircle(q1, q2);
        for (int i = 0; i < points.size(); i++) {
            Vector3f pi = points.get(i);
            if (!sphericalCircleContains(c, pi)) {
                c = sphericalCircle(q1, q2, pi);
            }
        }
        return c;
    }
    
    private Vector4f sphericalCircle(Vector3f p0, Vector3f p1) {
        Vector4f c = new Vector4f();
        // normal
        c.x = p0.x + p1.x;
        c.y = p0.y + p1.y;
        c.z = p0.z + p1.z;
        c.normalize();
        // radius
        c.w = c.x * p0.x + c.y * p0.y + c.z * p0.z;
        return c;
    }
    
    private Vector4f sphericalCircle(Vector3f p0, Vector3f p1, Vector3f p2) {
        // circle plane
        Vector3f v0 = new Vector3f();
        Vector3f v1 = new Vector3f();
        v0.sub(p1, p0);
        v1.sub(p2, p0);
        Vector3f n = new Vector3f();
        n.cross(v0, v1);
        n.normalize();
        // radius
        float r = n.x * p0.x + n.y * p0.y + n.z * p0.z;
        Vector4f c = new Vector4f(n.x, n.y, n.z, r);
        if (r < 0) {
            // make the normal face the same direction as input points
            c.scale(-1f);
        }
        return c;
    }
    
    private static boolean sphericalCircleContains(Vector4f c, Vector3f p) {
        return c.x * p.x + c.y * p.y + c.z * p.z >= c.w;
    }
    
    /*
     * Welzel
     */
    
    public static Vector4f minball(List<Vector3f> P) {
        return minball_b(P, Collections.<Vector3f>emptyList());
    }
    
    private static Vector4f minball_b(List<Vector3f> P, List<Vector3f> R) {
        Vector4f D;
        if (P.isEmpty() || R.size() == 4) {
            D = sphere(R);
        } else {
            //choose random p ∈ P;
            Vector3f p = P.get(P.size() - 1);
            D = minball_b(P.subList(0, P.size() - 1), R);
            if (!sphereContains(D, p)) {
                List<Vector3f> Rp = new ArrayList<>(R);
                Rp.add(p);
                D = minball_b(P.subList(0, P.size() - 1), Rp);
            }
        }
        return D;
    }
    
    private static Vector4f sphere(List<Vector3f> R) {
        if (R.isEmpty()) {
            return new Vector4f();
        } else if (R.size() == 1) {
            Vector3f p = R.get(0);
            return new Vector4f(p.x, p.y, p.z, 0);
        } else if (R.size() == 2) {
            return sphere(R.get(0), R.get(1), R.get(1), R.get(1));
        } else if (R.size() == 3) {
            return sphere(R.get(0), R.get(1), R.get(2), R.get(2));
        } else {
            return sphere(R.get(0), R.get(1), R.get(2), R.get(3));
        }
    }
    
    /*
     * MinCone
     */
    
    private Vector4f minCone(List<Vector3f> points) {
        Vector4f c = cone(points.get(0), points.get(1), points.get(2));
        for (int i = 3; i < points.size(); i++) {
            Vector3f pi = points.get(i);
            if (!coneContains(c, pi)) {
                c = minCone(points.subList(0, i), pi);
            }
        }
        return c;
    }
    
    private Vector4f minCone(List<Vector3f> points, Vector3f q) {
        Vector4f c = cone(points.get(0), points.get(1), q);
        for (int i = 2; i < points.size(); i++) {
            Vector3f pi = points.get(i);
            if (!coneContains(c, pi)) {
                c = minCone(points.subList(0, i), pi, q);
            }
        }
        return c;
    }
    
    private Vector4f minCone(List<Vector3f> points, Vector3f q1, Vector3f q2) {
        Vector4f c = cone(points.get(0), q1, q2);
        for (int i = 1; i < points.size(); i++) {
            Vector3f pi = points.get(i);
            if (!coneContains(c, pi)) {
                c = minCone(points.subList(0, i), pi, q1, q2);
            }
        }
        return c;
    }
    
    private Vector4f minCone(List<Vector3f> points, Vector3f q1, Vector3f q2, Vector3f q3) {
        Vector4f c = cone(q1, q2, q3);
        for (int i = 0; i < points.size(); i++) {
            Vector3f pi = points.get(i);
            if (!coneContains(c, pi)) {
                c = cone(q1, q2, q3, pi);
            }
        }
        return c;
    }
    
    private Vector4f cone(Vector3f p0, Vector3f p1, Vector3f p2) {
        return sphericalCircle(p0, p1, p2);
    }
    
    private Vector4f cone(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3) {
        // find all four circles
        Vector4f c0 = sphericalCircle(p0, p1, p2);
        Vector4f c1 = sphericalCircle(p0, p1, p3);
        Vector4f c2 = sphericalCircle(p0, p2, p3);
        Vector4f c3 = sphericalCircle(p1, p2, p3);
        
        // make circles cones
        if (!coneContains(c0, p3)) {
            c0.scale(-1f);
        }
        if (!coneContains(c1, p2)) {
            c1.scale(-1f);
        }
        if (!coneContains(c2, p1)) {
            c2.scale(-1f);
        }
        if (!coneContains(c3, p0)) {
            c3.scale(-1f);
        }
        
        // choose smallest cone
        Vector4f c = c0;
        if (c1.w > c.w) {
            c = c1;
        }
        if (c2.w > c.w) {
            c = c2;
        }
        if (c3.w > c.w) {
            c = c3;
        }
        
        return c;
    }
    
    private static boolean coneContains(Vector4f c, Vector3f p) {
        return sphericalCircleContains(c, p);
    }
    
    public static <T> List<T> rotate(List<T> list, int shift) {
        List<T> rotated = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            int li = (list.size() + i - shift) % list.size();
            rotated.add(list.get(li));
        }
        return rotated;
    }
    
    public static void checkCone(Vector4f c, List<Vector3f> l, String name) {
        boolean valid = true;
        for (Vector3f p : l) {
            if (!coneContains(c, p)) {
                System.out.println("Invalid point " + p);
                valid = false;
                break;
            }
        }
        System.out.println("Cone " + name + ": " + valid);
    }
    
}
