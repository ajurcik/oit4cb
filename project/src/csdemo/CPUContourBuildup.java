package csdemo;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JFrame;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

/**
 *
 * @author Adam Jurcik <xjurc@fi.muni.cz>
 */
public class CPUContourBuildup {
    
    private Molecule molecule;
    
    // settings
    private final int maxNeighbors;
    private final float probeRadius;
    
    private List<Vector4f> atoms;
    private int[] neighborCounts;
    private int[] neighbors;
    private Vector4f[] smallCircles;
    private boolean[] smallCirclesValid;

    public CPUContourBuildup(Molecule molecule, int maxNeighbors, float probeRadius) {
        this.molecule = molecule;
        // settings
        this.maxNeighbors = maxNeighbors;
        this.probeRadius = probeRadius;
    }
    
    public void computeNeighbors() {
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
        neighbors = new int[atoms.size() * maxNeighbors];
        smallCircles = new Vector4f[atoms.size() * maxNeighbors];
        smallCirclesValid = new boolean[atoms.size() * maxNeighbors];
        
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
                    if (count > maxNeighbors) {
                        throw new IllegalStateException("MAX_NEIGHBORS exceded. Neighbors: " + count);
                    }
                    neighbors[i * maxNeighbors + count] = j;
                    Vector4f smallCircle = new Vector4f();
                    float r = ((atom.w + probeRadius) * (atom.w + probeRadius))
                        + (dist * dist)
                        - ((other.w + probeRadius) * (other.w + probeRadius));
                    r = r / (2.0f * dist * dist);
                    // set small circle
                    vec.scale(r);
                    smallCircle.set(vec);
                    smallCircle.w = (float) Math.sqrt(((atom.w + probeRadius) * (atom.w + probeRadius)) - vec.dot(vec));
                    smallCircles[i * maxNeighbors + count] = smallCircle;
                    count++;
                }
            }
            neighborCounts[i] = count;
        }
        
        // print statistics
        int minNeighborCount = maxNeighbors;
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
        
        // print statistics
        int totalSmallCircles = 0;
        for (int i = 0; i < atoms.size(); i++) {
            for (int j = 0; j < neighborCounts[i]; j++) {
                if (smallCircles[i * maxNeighbors + j].w >= 0f) {
                    totalSmallCircles++;
                }
            }
        }
        System.out.println("Small circles (CPU): " + totalSmallCircles);
    }
    
    public void filterSmallCircles() {
        for (int x = 0; x < maxNeighbors; x++) {
            for (int y = 0; y < atoms.size(); y++) {
                // get atom index
                int atomIdx = y;
                // get neighbor atom index
                int jIdx = x;
                // set small circle visibility to false
                //smallCirclesVisible[atomIdx * maxNumNeighbors + jIdx] = 0;
                smallCirclesValid[atomIdx * maxNeighbors + jIdx] = true;
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
                int j = neighbors[atomIdx * maxNeighbors + jIdx];
                // get small circle j
                Vector4f scj = smallCircles[atomIdx * maxNeighbors + jIdx];
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
                        int k = neighbors[atomIdx * maxNeighbors + kCnt];
                        // pk = center of atom k
                        Vector4f ak = atoms.get(k);
                        Vector3f pk = new Vector3f(ak.x, ak.y, ak.z);
                        // get small circle k
                        Vector4f sck = smallCircles[atomIdx * maxNeighbors + kCnt];
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
//                                        SmallCirclesPlot plot = new SmallCirclesPlot(atomi, aj, ak, vj, vk, nj, nk, h, probeRadius);
//                                        plot.setPreferredSize(new Dimension(1280, 960));
//                                        
//                                        JFrame f = new JFrame("cap inside I");
//                                        f.add(plot);
//                                        f.pack();
//                                        f.setVisible(true);
                                    }
                                }
                            } else {
                                if (mj.dot(mk) > 0.0 && nj.dot(q) < 0.0) {
                                    // atom i has no contour
                                    neighborCounts[atomIdx] = 0;
                                    
//                                    SmallCirclesPlot plot = new SmallCirclesPlot(atomi, aj, ak, vj, vk, nj, nk, h, probeRadius);
//                                    plot.setPreferredSize(new Dimension(1280, 960));
//
//                                    JFrame f = new JFrame("no contours");
//                                    f.add(plot);
//                                    f.pack();
//                                    f.setVisible(true);
                                } else {
                                    // test cap inclusion
                                    if (testCapInclusion2(scj, vj, nj, sck, vk, nk, R)) {
//                                        SmallCirclesPlot plot = new SmallCirclesPlot(atomi, aj, ak, vj, vk, nj, nk, h, probeRadius);
//                                        plot.setPreferredSize(new Dimension(1280, 960));
//                                        
//                                        JFrame f = new JFrame("cap inside II");
//                                        f.add(plot);
//                                        f.pack();
//                                        f.setVisible(true);
                                    }
                                }
                            }
                        }
                    }
                }
                // all k were tested, see if j is cut off
                if (!addJ) {
                    smallCirclesValid[atomIdx * maxNeighbors + jIdx] = false;
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
    
    private boolean testCapInclusion2(Vector4f scj, Vector3f vj, Vector3f nj, Vector4f sck, Vector3f vk, Vector3f nk, float r) {
        float dj = ((float) Math.sqrt(r * r - scj.w * scj.w)) / r;
        float dk = ((float) Math.sqrt(r * r - sck.w * sck.w)) / r;
        if (vj.dot(nj) > 0f) {
            dj = -dj;
        }
        if (vk.dot(nk) > 0f) {
            dk = -dk;
        }
        float a = nj.dot(nk) - dj * dk;
        
        return dk <= dj && (a >= 1f || (a >= 0f && a*a >= (1f - dk * dk) * (1f - dj * dj)));
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
    
//    typedef struct {
//        uint key;
//        uint atomk;
//        uint index;
//        uint padding;
//    } arc_entry;

//    constant uint INVALID_KEY = 0xffffffff;
//    constant uint SECONDARY_FLAG = 0x80000000;
//    constant uint KEY_VALUE_MASK = 0x07ffffff;

    private final float TWO_PI = 6.28318530718f;

//    typedef struct {
//        uint threadCount;
//        uint hashCount;
//        uint hashErrorCount;
//        uint totalArcCount;
//    } counters_t;
//
//    typedef struct {
//        int atomsCount;
//        int maxNumNeighbors;
//        int maxNumTotalArcHashes;
//        int maxHashIterations;
//        float probeRadius;
//    } params_t;

    //float fmod(float x, float y);
//    uint packArcIndex(uint atomIdx, uint index);
//    void writeArcHash(uint i, uint j, uint k, uint index, bool primary,
//            global arc_entry * arcHashes, global counters_t * counters, constant params_t * params);

    public void computeArcs() {
        for (int atomIdx = 0; atomIdx < atoms.size(); atomIdx++) {
            for (int jIdx = 0; jIdx < neighborCounts[atomIdx]; jIdx++) {
                computeArcs(atomIdx, jIdx);
            }
        }
    }
    
    public void computeArcs(int atomIdx, int jIdx) {
        // read position and radius of atom i from sorted array
        Vector4f atomi = atoms.get(atomIdx);
        Vector3f pi = new Vector3f(atomi.x, atomi.y, atomi.z);
        float R = atomi.w + probeRadius;

        // the atom index of j
        //int j = neighbors[atomIdx * maxNeighbors + jIdx];
        // get small circle j
        Vector4f scj = smallCircles[atomIdx * maxNeighbors + jIdx];
        // do nothing if small circle j has radius -1 (removed)
        if (!smallCirclesValid[atomIdx * maxNeighbors + jIdx] /*scj.w < -10.0f*/) { // TODO
            //arcCount[atomIdx * maxNumNeighbors + jIdx] = 0; // DEBUG
            return;
        }
        scj.w = Math.abs(scj.w);
        // vj = the small circle center
        Vector3f vj = new Vector3f(scj.x, scj.y, scj.z);
        // pj = center of atom j
        //float4 aj = atoms[j]; // NOT REFERENCED?
        //float4 aj = read_imagef(atoms, sampler, (int2)(j)); // PROFILE
        //float3 pj = aj.xyz; // NOT REFERENCED?
        // store all arcs
        float start[] = new float[64];
        float end[] = new float[64];
        int startkIndex[] = new int[64];
        int endkIndex[] = new int[64];
        boolean arcValid[] = new boolean[64];
        start[0] = 0.0f;
        end[0] = TWO_PI;
        startkIndex[0] = 0;
        endkIndex[0] = 0;
        arcValid[0] = true;
        int arcCnt = 1;
        // temporary arc arrays for new arcs
        float tmpStart[] = new float[16];
        float tmpEnd[] = new float[16];
        int tmpStartkIndex[] = new int[16];
        int tmpEndkIndex[] = new int[16];
        int tmpArcCnt = 0;
        // compute axes of local coordinate system
        Vector3f ex = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f ey = new Vector3f(0.0f, 1.0f, 0.0f);
        //float3 xAxis = cross(vj, ey);
        Vector3f xAxis = new Vector3f();
        xAxis.cross(vj, ey);
        if (xAxis.dot(xAxis) == 0.0f) {
            xAxis.cross(vj, ex);
        }
        xAxis.normalize();
        //float3 yAxis = cross(xAxis, vj);
        Vector3f yAxis = new Vector3f();
        yAxis.cross(xAxis, vj);
        yAxis.normalize();

        // check j with all other neighbors k
        for (int kCnt = 0; kCnt < neighborCounts[atomIdx]; kCnt++) {
            // don't compare the circle with itself
            if (jIdx == kCnt) { 
                continue;
            }
            // the atom index of k
            int k = neighbors[atomIdx * maxNeighbors + kCnt];
            // get small circle k
            Vector4f sck = smallCircles[atomIdx * maxNeighbors + kCnt];
            // do nothing if small circle k has radius -1 (removed)
            if (!smallCirclesValid[atomIdx * maxNeighbors + kCnt] /*sck.w < -10.0f*/) {
                continue;
            }
            sck.w = Math.abs(sck.w);
            // vk = the small circle center
            Vector3f vk = new Vector3f(sck.x, sck.y, sck.z);
            // pk = center of atom k
            Vector4f ak = atoms.get(k);
            Vector3f pk = new Vector3f(ak.x, ak.y, ak.z);
            // vj * vk
            float vjvk = vj.dot(vk);
            // denominator
            float denom = vj.dot(vj) * vk.dot(vk) - vjvk * vjvk;
            // point on straight line (intersection of small circle planes)
            //float3 h = vj * (dot(vj, vj - vk) * dot(vk, vk)) / denom + vk * (dot(vk - vj, vk) * dot(vj, vj)) / denom;
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
            // do nothing if h is outside of the extended sphere of atom i
            if (h.length() > R) { 
                continue;
            }
            // compute the root
            Vector3f crossvkvj = new Vector3f();
            crossvkvj.cross(vk, vj);
            float root = (float) Math.sqrt((R * R - h.dot(h)) / crossvkvj.dot(crossvkvj));
            // compute the two intersection points
            //float3 x1 = h + cross(vk, vj) * root;
            Vector3f x1 = new Vector3f(crossvkvj);
            x1.scale(root);
            x1.add(h);
            //float3 x2 = h - cross(vk, vj) * root;
            Vector3f x2 = new Vector3f(crossvkvj);
            x2.scale(-root);
            x2.add(h);
            // swap x1 & x2 if vj points in the opposit direction of pj-pi
            Vector3f subpkpi = new Vector3f();
            subpkpi.sub(pk, pi);
            if (vk.dot(subpkpi) < 0.0f) {
                Vector3f tmpVec = x1;
                x1 = x2;
                x2 = tmpVec;
            }
            // transform x1 and x2 to small circle coordinate system
            Vector3f subx1vj = new Vector3f();
            Vector3f subx2vj = new Vector3f();
            subx1vj.sub(x1, vj);
            subx2vj.sub(x2, vj);
            float xX1 = subx1vj.dot(xAxis);
            float yX1 = subx1vj.dot(yAxis);
            float xX2 = subx2vj.dot(xAxis);
            float yX2 = subx2vj.dot(yAxis);
            float angleX1 = (float) Math.atan2(yX1, xX1);
            float angleX2 = (float) Math.atan2(yX2, xX2);
            // limit angles to 0..2*PI
            if (angleX1 > TWO_PI) {
                angleX1 = angleX1 % TWO_PI;
                angleX2 = angleX2 % TWO_PI;
            }
            // angle of x2 has to be larger than angle of x1 (add 2 PI)
            if (angleX2 < angleX1) {
                angleX2 += TWO_PI;
            }
            // make all angles positive (add 2 PI)
            if (angleX1 < 0.0f) {
                angleX1 += TWO_PI;
                angleX2 += TWO_PI;
            }

            // check all existing arcs with new arc k
            for (int aCnt = 0; aCnt < arcCnt; aCnt++) {
                float s = start[aCnt];
                float e = end[aCnt];
                int skIndex = startkIndex[aCnt];
                int ekIndex = endkIndex[aCnt];
                if (arcValid[aCnt]) {
                    if (angleX1 < s) {
                        // case (1) & (10)
                        if ((s - angleX1) > (angleX2 - angleX1)) {
                            if (((s - angleX1) + (e - s)) > TWO_PI) {
                                if (((s - angleX1) + (e - s)) < (TWO_PI + angleX2 - angleX1)) {
                                    // case (10)
                                    start[aCnt] = angleX1;
                                    startkIndex[aCnt] = k;
                                    end[aCnt] = e % TWO_PI; // fmod
                                    // second angle check
                                    if (end[aCnt] < start[aCnt]) {
                                        end[aCnt] += TWO_PI;
                                    }
                                } else {
                                    start[aCnt] = angleX1;
                                    startkIndex[aCnt] = k;
                                    end[aCnt] = angleX2 % TWO_PI; // fmod
                                    endkIndex[aCnt] = k;
                                    // second angle check
                                    if (end[aCnt] < start[aCnt]) {
                                        end[aCnt] += TWO_PI;
                                    }
                                }
                            } else {
                                // case (1)
                                //arcAngles.RemoveAt(aCnt);
                                //aCnt--;
                                arcValid[aCnt] = false;
                            }
                        } else {
                            if (((s - angleX1) + (e - s)) > (angleX2 - angleX1)) {
                                // case (5)
                                end[aCnt] = angleX2 % TWO_PI; // fmod
                                endkIndex[aCnt] = k;
                                // second angle check
                                if (end[aCnt] < start[aCnt]) {
                                    end[aCnt] += TWO_PI;
                                }
                                if (((s - angleX1) + (e - s)) > TWO_PI) {
                                    // case (6)
                                    tmpStart[tmpArcCnt] = angleX1;
                                    tmpStartkIndex[tmpArcCnt] = k;
                                    tmpEnd[tmpArcCnt] = e % TWO_PI; // fmod
                                    tmpEndkIndex[tmpArcCnt] = ekIndex;
                                    // second angle check
                                    if (tmpEnd[tmpArcCnt] < tmpStart[tmpArcCnt]) {
                                        tmpEnd[tmpArcCnt] += TWO_PI;
                                    }
                                    tmpArcCnt++;
                                }
                            }
                        } // case (4): Do nothing!
                    } else { // angleX1 > s
                        // case (2) & (9)
                        if ((angleX1 - s) > (e - s)) {
                            if (((angleX1 - s) + (angleX2 - angleX1)) > TWO_PI) {
                                if (((angleX1 - s) + (angleX2 - angleX1)) < (TWO_PI + e - s)) {
                                    // case (9)
                                    end[aCnt] = angleX2 % TWO_PI; // fmod
                                    endkIndex[aCnt] = k;
                                    // second angle check
                                    if (end[aCnt] < start[aCnt]) {
                                        end[aCnt] += TWO_PI;
                                    }
                                }
                            } else {
                                // case (2)
                                //arcAngles.RemoveAt( aCnt);
                                //aCnt--;
                                arcValid[aCnt] = false;
                            }
                        } else {
                            if (((angleX1 - s) + (angleX2 - angleX1)) > (e - s)) {
                                // case (7)
                                start[aCnt] = angleX1;
                                startkIndex[aCnt] = k;
                                // second angle check
                                end[aCnt] = end[aCnt] % TWO_PI; // fmod
                                if (end[aCnt] < start[aCnt]) {
                                    end[aCnt] += TWO_PI;
                                }
                                if (((angleX1 - s) + (angleX2 - angleX1)) > TWO_PI) {
                                    // case (8)
                                    tmpStart[tmpArcCnt] = s;
                                    tmpStartkIndex[tmpArcCnt] = skIndex;
                                    tmpEnd[tmpArcCnt] = angleX2 % TWO_PI; // fmod
                                    tmpEndkIndex[tmpArcCnt] = k;
                                    // second angle check
                                    if (tmpEnd[tmpArcCnt] < tmpStart[tmpArcCnt]) {
                                        tmpEnd[tmpArcCnt] += TWO_PI;
                                    }
                                    tmpArcCnt++;
                                }
                            } else {
                                // case (3)
                                start[aCnt] = angleX1;
                                startkIndex[aCnt] = k;
                                end[aCnt] = angleX2 % TWO_PI; // fmod
                                endkIndex[aCnt] = k;
                                // second angle check
                                if (end[aCnt] < start[aCnt]) {
                                    end[aCnt] += TWO_PI;
                                }
                            }
                        }
                    }
                } // if (arcValid[aCnt])
            } // for (uint aCnt = 0; aCnt < arcCnt; aCnt++)

            // debug
//            if (tmpArcCnt > 16 || arcCnt > 32) {
//                counters->hashErrorCount = tmpArcCnt * 1000;
//            }
//            if (arcCnt > 32) {
//                counters->hashErrorCount = arcCnt * 1000;
//            }

            // copy new arcs to arc array
            for (int aCnt = 0; aCnt < tmpArcCnt; aCnt++) {
                start[aCnt + arcCnt] = tmpStart[aCnt];
                end[aCnt + arcCnt] = tmpEnd[aCnt];
                startkIndex[aCnt + arcCnt] = tmpStartkIndex[aCnt];
                endkIndex[aCnt + arcCnt] = tmpEndkIndex[aCnt];
                arcValid[aCnt + arcCnt] = true;
            }
            // add new arcs to arc count
            arcCnt += tmpArcCnt;
            // "reset" temporary arc array
            tmpArcCnt = 0;

            // fill gaps (overwrite invalid arcs)
            int counter = 0;
            for (int aCnt = 0; aCnt < arcCnt; aCnt++) {
                if (arcValid[aCnt]) {
                    start[aCnt - counter] = start[aCnt];
                    end[aCnt - counter] = end[aCnt];
                    startkIndex[aCnt - counter] = startkIndex[aCnt];
                    endkIndex[aCnt - counter] = endkIndex[aCnt];
                    arcValid[aCnt - counter] = arcValid[aCnt];
                } else {
                    counter++;
                }
            }
            // subtract number of invalid arcs from total number of arcs
            arcCnt -= counter;
        } // for (uint kCnt = 0; kCnt < numNeighbors; kCnt++)

        // TODO: remove/merge split arcs ( x..2*PI / 0..y --> x..y+2*PI )

        // merge arcs if arc with angle 0 and arc with angle 2*PI exist
        int idx0 = -1;
        int idx2pi = -1;
        for (int aCnt = 0; aCnt < arcCnt; aCnt++) {
            if (start[aCnt] < 0.00001f) {
                idx0 = (int)aCnt;
            } else if (Math.abs(end[aCnt] - TWO_PI) < 0.0001f) {
                idx2pi = (int)aCnt;
            }
        }
        if (idx0 >= 0 && idx2pi >= 0) {
            start[(int)idx0] = start[(int)idx2pi];
            startkIndex[(int)idx0] = startkIndex[(int)idx2pi];
            // second angle check
            end[(int)idx0] = end[(int)idx0] % TWO_PI; // fmod
            if (end[(int)idx0] < start[(int)idx0]) {
                end[(int)idx0] += TWO_PI;
            }
            // fill gaps (overwrite removed arc idx2pi)
            for (int aCnt = idx2pi; aCnt < arcCnt - 1; aCnt++) {
                start[aCnt] = start[aCnt + 1];
                end[aCnt] = end[aCnt + 1];
                startkIndex[aCnt] = startkIndex[aCnt + 1];
                endkIndex[aCnt] = endkIndex[aCnt + 1];
                arcValid[aCnt] = true;
            }
            // subtract the removed arc from total number of arcs
            arcCnt--;
        }

//        int arcWritten = 0;
        // copy arcs to global arc array
//        for (uint aCnt = 0; aCnt < arcCnt; aCnt++) {
//            if (atomIdx < j) {
//                uint k = startkIndex[aCnt];
//                if (j < k) {
//                    uint index = atomic_add(&counters->totalArcCount, 1);
//                    //uint index = atomicCounterIncrement(totalArcCount);
//                    //uint index = atomIdx * maxNumNeighbors * maxNumArcs + jIdx * maxNumArcs + arcWritten;
//                    arcs[index] = (float4)(pi + vj + (cos(start[aCnt]) * xAxis + sin(start[aCnt]) * yAxis) * scj.w,
//                            (float)k + 0.2f); //start[aCnt]);
//                    writeArcHash(atomIdx, j, k, index, true, arcHashes, counters, params);
//                    writeArcHash(atomIdx, k, j, index, false, arcHashes, counters, params);
//                    writeArcHash(j, k, atomIdx, index, false, arcHashes, counters, params);
//                    arcWritten++;
//                }
//                k = endkIndex[aCnt];
//                if (j < k) {
//                    uint index = atomic_add(&counters->totalArcCount, 1);
//                    //uint index = atomicCounterIncrement(totalArcCount);
//                    //uint index = atomIdx * maxNumNeighbors * maxNumArcs + jIdx * maxNumArcs + arcWritten;
//                    arcs[index] = (float4)(pi + vj + (cos(end[aCnt]) * xAxis + sin(end[aCnt]) * yAxis) * scj.w,
//                            (float)k + 0.2f); //end[aCnt]);
//                    writeArcHash(atomIdx, j, k, index, true, arcHashes, counters, params);
//                    writeArcHash(atomIdx, k, j, index, false, arcHashes, counters, params);
//                    writeArcHash(j, k, atomIdx, index, false, arcHashes, counters, params);
//                    arcWritten++;
//                }
//            }
//        }
//
//        // write number of arcs
//        arcCount[atomIdx * params->maxNumNeighbors + jIdx] = arcWritten;

        // set small circle j visible if at least one arc was created and i < j
        if (arcCnt > 0) {
        //if( arcWritten > 0 ) {
            //if (atomIdx < j) {
//                smallCirclesVisible[atomIdx * params->maxNumNeighbors + jIdx] = 1;
            //}
        }
        // DO NOT USE THIS!! It will create false, internal arcs!
        //if( arcCnt == 0 ) {
        //	smallCircles[atomIdx * params.maxNumNeighbors + jIdx].w = -1.0f;
        //}
        //atomicCounterIncrement(threadCount); // DEBUG
    }
    
}
