package csdemo;

import static csdemo.Scene.MAX_NEIGHBORS;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class CPUContourBuildup {
    
    private Molecule molecule;
    
    private int[] neighborCount;
    private int[] neighbors;
    private Vector4f[] smallCircles;

    public CPUContourBuildup(Molecule molecule) {
        this.molecule = molecule;
    }
    
    public void countSmallCircles(float probeRadius) {
        float[] positions = molecule.getAtomPositions(0);
        
        List<Vector4f> atoms = new ArrayList<>();
        for (int i = 0; i < molecule.getAtomCount(); i++) {
            Vector4f atom = new Vector4f();
            atom.x = positions[3 * i];
            atom.y = positions[3 * i + 1];
            atom.z = positions[3 * i + 2];
            atom.w = molecule.getAtom(i).r;
            atoms.add(atom);
        }
        
        neighborCount = new int[atoms.size()];
        neighbors = new int[atoms.size() * MAX_NEIGHBORS];
        smallCircles = new Vector4f[atoms.size() * MAX_NEIGHBORS];
        
        // find small circles (brute force)
        Arrays.fill(neighborCount, 0);
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
            neighborCount[i] = count;
        }
        
        // print statistics
        int minNeighborCount = MAX_NEIGHBORS;
        int maxNeighborCount = 0;
        int totalNeighbors = 0;
        for (int i = 0; i < atoms.size(); i++) {
            if (neighborCount[i] < minNeighborCount) {
                minNeighborCount = neighborCount[i];
            }
            if (neighborCount[i] > maxNeighborCount) {
                maxNeighborCount = neighborCount[i];
            }
            totalNeighbors += neighborCount[i];
        }
        System.out.println("Min. neighbor count (CPU): " + minNeighborCount);
        System.out.println("Max. neighbor count (CPU): " + maxNeighborCount);
        System.out.println("Avg. neighbor ocunt (CPU): " + totalNeighbors / (float) atoms.size());
        System.out.println("Neighbors (CPU): " + totalNeighbors);
        
        // remove covered small circles
        for (int i = 0; i < atoms.size(); i++) {
            for (int jIdx = 0; jIdx < neighborCount[i]; jIdx++) {
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
                for (int kCnt = 0; kCnt < neighborCount[i]; kCnt++) {
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
                                    neighborCount[i] = 0;
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
        
        // print statistics
        int totalSmallCircles = 0;
        for (int i = 0; i < atoms.size(); i++) {
            for (int j = 0; j < neighborCount[i]; j++) {
                if (smallCircles[i * MAX_NEIGHBORS + j].w >= 0f) {
                    totalSmallCircles++;
                }
            }
        }
        System.out.println("Small circles (CPU): " + totalSmallCircles);
    }
    
}
