package csdemo;

import static csdemo.Scene.MAX_NEIGHBORS;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class CPUContourBuildup {
    
    private Molecule molecule;
    
    private List<Vector4f> atoms;
    private int[] neighborCounts;
    private int[] neighbors;
    private Vector4f[] smallCircles;

    public CPUContourBuildup(Molecule molecule) {
        this.molecule = molecule;
    }
    
    public void computeNeighbors(float probeRadius) {
        float[] positions = molecule.getAtomPositions(0);
        
        atoms = new ArrayList<>();
        for (int i = 0; i < molecule.getAtomCount(); i++) {
            Vector4f atom = new Vector4f();
            atom.x = positions[3 * i];
            atom.y = positions[3 * i + 1];
            atom.z = positions[3 * i + 2];
            atom.w = molecule.getAtom(i).r;
            atoms.add(atom);
        }
        
        neighborCounts = new int[atoms.size()];
        neighbors = new int[atoms.size() * MAX_NEIGHBORS];
        smallCircles = new Vector4f[atoms.size() * MAX_NEIGHBORS];
        
        // find small circles (brute force)
        Arrays.fill(neighborCounts, 0);
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
                    if (count > MAX_NEIGHBORS) {
                        throw new IllegalStateException("MAX_NEIGHBORS exceded. Neighbors: " + count);
                    }
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
            if (count == 0) {
                int brk = 1;
            }
            neighborCounts[i] = count;
        }
        
        // print statistics
        int minNeighborCount = MAX_NEIGHBORS;
        int maxNeighborCount = 0;
        int totalNeighbors = 0;
        for (int i = 0; i < atoms.size(); i++) {
            if (neighborCounts[i] < minNeighborCount) {
                minNeighborCount = neighborCounts[i];
            }
            if (neighborCounts[i] > maxNeighborCount) {
                maxNeighborCount = neighborCounts[i];
            }
            totalNeighbors += neighborCounts[i];
        }
        System.out.println("Min. neighbor count (CPU): " + minNeighborCount);
        System.out.println("Max. neighbor count (CPU): " + maxNeighborCount);
        System.out.println("Avg. neighbor ocunt (CPU): " + totalNeighbors / (float) atoms.size());
        System.out.println("Neighbors (CPU): " + totalNeighbors);
        
//        // remove covered small circles
//        for (int i = 0; i < atoms.size(); i++) {
//            for (int jIdx = 0; jIdx < neighborCounts[i]; jIdx++) {
//                Vector4f atomi = atoms.get(i);
//                Vector3f pi = new Vector3f(atomi.x, atomi.y, atomi.z);
//                float R = atomi.w + probeRadius;
//
//                // flag wether j should be added (true) is cut off (false)
//                boolean addJ = true;
//
//                // the atom index of j
//                int j = neighbors[i * MAX_NEIGHBORS + jIdx];
//                // get small circle j
//                Vector4f scj = smallCircles[i * MAX_NEIGHBORS + jIdx];
//                // vj = the small circle center
//                Vector3f vj = new Vector3f(scj.x, scj.y, scj.z);
//                // pj = center of atom j
//                Vector4f aj = atoms.get(j);
//                Vector3f pj = new Vector3f(aj.x, aj.y, aj.z);
//
//                // check j with all other neighbors k
//                for (int kCnt = 0; kCnt < neighborCounts[i]; kCnt++) {
//                    // don't compare the circle with itself
//                    if (jIdx != kCnt) {
//                        // the atom index of k
//                        int k = neighbors[i * MAX_NEIGHBORS + kCnt];
//                        // pk = center of atom k
//                        Vector4f ak = atoms.get(k);
//                        Vector3f pk = new Vector3f(ak.x, ak.y, ak.z);
//                        // get small circle k
//                        Vector4f sck = smallCircles[i * MAX_NEIGHBORS + kCnt];
//                        // vk = the small circle center
//                        Vector3f vk = new Vector3f(sck.x, sck.y, sck.z);
//                        // vj * vk
//                        float vjvk = vj.dot(vk);
//                        // denominator
//                        float denom = vj.dot(vj) * vk.dot(vk) - vjvk * vjvk;
//                        // point on straight line (intersection of small circle planes)
//                        Vector3f vjmvk = new Vector3f();
//                        Vector3f vkmvj = new Vector3f();
//                        vjmvk.sub(vj, vk);
//                        vkmvj.sub(vk, vj);
//                        Vector3f hvj = new Vector3f(vj);
//                        Vector3f hvk = new Vector3f(vk);
//                        hvj.scale(vj.dot(vjmvk) * vk.dot(vk) / denom);
//                        hvk.scale(vk.dot(vkmvj) * vj.dot(vj) / denom);
//                        Vector3f h = new Vector3f();
//                        h.add(hvj, hvk);
//                        // compute cases
//                        Vector3f nj = new Vector3f();
//                        nj.sub(pi, pj);
//                        nj.normalize();
//                        Vector3f nk = new Vector3f();
//                        nk.sub(pi, pk);
//                        nk.normalize();
//                        Vector3f q = new Vector3f();
//                        q.sub(vk, vj);
//                        // if normals are the same (unrealistic, yet theoretically possible)
//                        if (nj.dot(nk) == 1.0f) {
//                            if (nj.dot(nk) > 0.0f) { // Redundant?
//                                if (nj.dot(q) > 0.0f) {
//                                    // k cuts off j --> remove j
//                                    addJ = false;
//                                }
//                            }
//                        } else if (h.length() > R) {
//                            Vector3f mj = new Vector3f();
//                            Vector3f mk = new Vector3f();
//                            mj.sub(vj, h);
//                            mk.sub(vk, h);
//                            if (nj.dot(nk) > 0.0f) {
//                                if (mj.dot(mk) > 0.0f && nj.dot(q) > 0.0f) {
//                                    // k cuts off j --> remove j
//                                    addJ = false;
//                                }
//                            } else {
//                                if (mj.dot(mk) > 0.0f && nj.dot(q) < 0.0f) {
//                                    // atom i has no contour
//                                    neighborCounts[i] = 0;
//                                }
//                            }
//                        }
//                    }
//                }
//                // all k were tested, see if j is cut off
//                if (!addJ) {
//                    smallCircles[i * MAX_NEIGHBORS + jIdx].w = -1.0f;
//                }
//            }
//        }
//        
//        // print statistics
//        int totalSmallCircles = 0;
//        for (int i = 0; i < atoms.size(); i++) {
//            for (int j = 0; j < neighborCounts[i]; j++) {
//                if (smallCircles[i * MAX_NEIGHBORS + j].w >= 0f) {
//                    totalSmallCircles++;
//                }
//            }
//        }
//        System.out.println("Small circles (CPU): " + totalSmallCircles);
    }
    
    public void filterSmallCircles(final float probeRadius) {
        for (int x = 0; x < MAX_NEIGHBORS; x++) {
            for (int y = 0; y < atoms.size(); y++) {
                // get atom index
                int atomIdx = y;
                // get neighbor atom index
                int jIdx = x;
                // set small circle visibility to false
                //smallCirclesVisible[atomIdx * maxNumNeighbors + jIdx] = 0;
                // check, if neighbor index is within bounds
                int numNeighbors = neighborCounts[atomIdx];
                if (jIdx >= numNeighbors) continue;

                // read position and radius of atom i from sorted array
                Vector4f atomi = atoms.get(atomIdx);
                Vector3f pi = new Vector3f(atomi.x, atomi.y, atomi.z);
                float R = atomi.w + probeRadius;

                // flag wether j should be added (true) is cut off (false)
                boolean addJ = true;

                // the atom index of j
                int j = neighbors[atomIdx * MAX_NEIGHBORS + jIdx];
                // get small circle j
                Vector4f scj = smallCircles[atomIdx * MAX_NEIGHBORS + jIdx];
                // vj = the small circle center
                Vector3f vj = new Vector3f(scj.x, scj.y, scj.z);
                // pj = center of atom j
                Vector4f aj = atoms.get(j);
                Vector3f pj = new Vector3f(aj.x, aj.y, aj.z);

                // check j with all other neighbors k
                for (int kCnt = 0; kCnt < numNeighbors; kCnt++) {
                    // don't compare the circle with itself
                    if (jIdx != kCnt) {
                        // the atom index of k
                        int k = neighbors[atomIdx * MAX_NEIGHBORS + kCnt];
                        // pk = center of atom k
                        Vector4f ak = atoms.get(k);
                        Vector3f pk = new Vector3f(ak.x, ak.y, ak.z);
                        // get small circle k
                        Vector4f sck = smallCircles[atomIdx * MAX_NEIGHBORS + kCnt];
                        // vk = the small circle center
                        Vector3f vk = new Vector3f(sck.x, sck.y, sck.z);
                        // vj * vk
                        float vjvk = vj.dot(vk);
                        // denominator
                        float denom = vj.dot(vj) * vk.dot(vk) - vjvk * vjvk;
                        // point on straight line (intersection of small circle planes)
                        // h = vj * (dot(vj, vj - vk) * dot(vk, vk)) / denom + vk * (dot(vk - vj, vk) * dot(vj, vj)) / denom;
                        Vector3f h = new Vector3f();
                        Vector3f subvjvk = new Vector3f();
                        Vector3f subvkvj = new Vector3f();
                        subvjvk.sub(vj, vk);
                        subvkvj.sub(vk, vj);
                        Vector3f hj = new Vector3f(vj);
                        hj.scale((vj.dot(subvjvk) * vk.dot(vk)) / denom);
                        Vector3f hk = new Vector3f(vk);
                        hk.scale((subvkvj.dot(vk) * vj.dot(vj)) / denom);
                        h.add(hj, hk);
                        // compute cases
                        // nj = normalize(pi - pk);
                        Vector3f nj = new Vector3f();
                        nj.sub(pi, pj);
                        nj.normalize();
                        // nk = normalize(pi - pk);
                        Vector3f nk = new Vector3f();
                        nk.sub(pi, pk);
                        nk.normalize();
                        // q = vk - vj;
                        Vector3f q = new Vector3f();
                        q.sub(vk, vj);
                        // if normals are the same (unrealistic, yet theoretically possible)
                        if (nj.dot(nk) == 1.0) {
                            if (nj.dot(nk) > 0.0 /*Redundant?*/) {
                                if (nj.dot(q) > 0.0) {
                                    // k cuts off j --> remove j
                                    addJ = false;
                                }
                            }
                        } else if (h.length() > R) {
                            // mj = (vj - h);
                            Vector3f mj = new Vector3f();
                            mj.sub(vj, h);
                            // mk = (vk - h);
                            Vector3f mk = new Vector3f();
                            mk.sub(vk, h);
                            if (nj.dot(nk) > 0.0) {
                                if (mj.dot(mk) > 0.0 && nj.dot(q) > 0.0) {
                                    // k cuts off j --> remove j
                                    addJ = false;
                                } else {
                                    if (testCapInclusion(scj, vj, nj, sck, vk, nk)) {
                                        SmallCirclesPlot plot = new SmallCirclesPlot(atomi, aj, ak, vj, vk, nj, nk, h, probeRadius);
                                        plot.setPreferredSize(new Dimension(800, 600));
                                        
                                        JFrame f = new JFrame();
                                        f.add(plot);
                                        f.pack();
                                        f.setVisible(true);
                                    }
                                }
                            } else {
                                if (mj.dot(mk) > 0.0 && nj.dot(q) < 0.0) {
                                    // atom i has no contour
                                    neighborCounts[atomIdx] = 0;
                                } else {
                                    // test cap inclusion
                                    if (testCapInclusion(scj, vj, nj, sck, vk, nk)) {
                                        SmallCirclesPlot plot = new SmallCirclesPlot(atomi, aj, ak, vj, vk, nj, nk, h, probeRadius);
                                        plot.setPreferredSize(new Dimension(800, 600));
                                        
                                        JFrame f = new JFrame();
                                        f.add(plot);
                                        f.pack();
                                        f.setVisible(true);
                                    }
                                }
                            }
                        }
                    }
                }
                // all k were tested, see if j is cut off
                if (!addJ) {
//                    smallCircles[atomIdx * MAX_NEIGHBORS + jIdx].w = -11.0f;
                }
            }
        }
    }
    
    private boolean testCapInclusion(Vector4f scj, Vector3f vj, Vector3f nj, Vector4f sck, Vector3f vk, Vector3f nk) {
        // get point on small circle j
        Vector3f cj = new Vector3f(vj);
        cj.normalize();
        Vector3f scjp = perp(cj);
        scjp.normalize();
        scjp.scale(scj.w);
        scjp.add(vj);
        
        // point on small circle k
        Vector3f ck = new Vector3f(vk);
        ck.normalize();
        Vector3f sckp = perp(ck);
        sckp.normalize();
        sckp.scale(sck.w);
        sckp.add(vk);

        scjp.normalize();
        sckp.normalize();
        float cosj = -nj.dot(scjp);
        float cosk = -nk.dot(sckp);
        float cosjk = nj.dot(nk);
        
        return Math.acos(cosk) > Math.acos(cosjk) + Math.acos(cosj);
    }
    
    private Vector3f perp(Vector3f v) {
        Vector3f v2;
        if (-0.9f < v.x && v.x < 0.9f) {
            v2 = new Vector3f(1f, 0f, 0f);
        } else {
            v2 = new Vector3f(0f, 1f, 0f);
        }
        
        Vector3f vp = new Vector3f();
        vp.cross(v, v2);
        
        return vp;
    }
    
}
