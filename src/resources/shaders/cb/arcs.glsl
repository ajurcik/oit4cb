#version 430 core

struct arc_entry {
    uint key;
    uint atomk;
    uint index;
    uint padding; 
};

const uint INVALID_KEY = 0xffffffff;
const uint SECONDARY_FLAG = 0x80000000;
const uint KEY_VALUE_MASK = 0x07ffffff;

const float TWO_PI = 6.28318530718;

uniform uint atomsCount;
uniform uint maxNumNeighbors;
uniform uint maxNumTotalArcHashes;
uniform uint maxHashIterations;

uniform float probeRadius;

uniform samplerBuffer atomsTex;
uniform usamplerBuffer neighborsTex;
uniform usamplerBuffer neighborCountsTex;
uniform samplerBuffer smallCirclesTex;

//layout(binding = 0) uniform atomic_uint threadCount;
//layout(binding = 0) uniform atomic_uint hashCount;
//layout(binding = 0) uniform atomic_uint hashErrorCount;
//layout(binding = 0) uniform atomic_uint totalArcCount;

layout(std430) buffer Arcs {
    vec4 arcs[];
};

layout(std430) buffer ArcCounts {
    uint arcCount[];
};

layout(std430) buffer ArcHashes {
    arc_entry arcHashes[];
};

layout(std430) buffer SmallCirclesVisible {
    uint smallCirclesVisible[];
};

layout(std430) buffer CountersBuffer {
    uint threadCount;
    uint hashCount;
    uint hashErrorCount;
    uint totalArcCount;
};

layout(local_size_x = 32, local_size_y = 8) in;

float fmod(float x, float y);
uint packArcIndex(uint atomIdx, uint index);
void writeArcHash(uint i, uint j, uint k, uint index, bool primary);

void main() {
    uint globalIdx = 0; //gl_LocalInvocationIndex * 90;
    // get atom index
    uint atomIdx = gl_GlobalInvocationID.y;
    // get neighbor atom index
    uint jIdx = gl_GlobalInvocationID.x;
    // check, if atom index is within bounds
    if (atomIdx >= atomsCount) return;
    // check, if neighbor index is within bounds
    if (jIdx >= maxNumNeighbors) return;
    // check, if neighbor index is within bounds
    //uint numNeighbors = neighborsCount[atomIdx];
    uint numNeighbors = texelFetch(neighborCountsTex, int(atomIdx)).r; // PROFILE
    if (jIdx >= numNeighbors) return;
    
    // read position and radius of atom i from sorted array
    //vec4 atomi = positions[atomIdx];
    vec4 atomi = texelFetch(atomsTex, int(atomIdx)); // PROFILE
    vec3 pi = atomi.xyz;
    float R = atomi.w + probeRadius;

    // the atom index of j
    //uint j = neighbors[atomIdx * params.maxNumNeighbors + jIdx];
    //uint j = neighbors[atomIdx * maxNumNeighbors + jIdx];
    uint j = texelFetch(neighborsTex, int(atomIdx * maxNumNeighbors + jIdx)).r; // PROFILE
    // get small circle j
    //vec4 scj = smallCircles[atomIdx * maxNumNeighbors + jIdx];
    vec4 scj = texelFetch(smallCirclesTex, int(atomIdx * maxNumNeighbors + jIdx)); // PROFILE
    // do nothing if small circle j has radius -1 (removed)
    if (scj.w < 0.0) {
        //arcCount[atomIdx * maxNumNeighbors + jIdx] = 0; // DEBUG
        return;
    }
    //atomicCounterIncrement(threadCount); // DEBUG
    // vj = the small circle center
    vec3 vj = scj.xyz;
    // pj = center of atom j
    //vec4 aj = positions[j];
    vec4 aj = texelFetch(atomsTex, int(j)); // PROFILE
    vec3 pj = aj.xyz;
    // store all arcs
    float start[64];
    float end[64];
    uint startkIndex[64];
    uint endkIndex[64];
    bool arcValid[64];
    start[globalIdx + 0] = 0.0;
    end[0] = TWO_PI;
    startkIndex[0] = 0;
    endkIndex[0] = 0;
    arcValid[0] = true;
    uint arcCnt = 1;
    // temporary arc arrays for new arcs
    float tmpStart[16];
    float tmpEnd[16];
    uint tmpStartkIndex[16];
    uint tmpEndkIndex[16];
    uint tmpArcCnt = 0;
    // compute axes of local coordinate system
    vec3 ex = vec3(1.0, 0.0, 0.0);
    vec3 ey = vec3(0.0, 1.0, 0.0);
    vec3 xAxis = cross(vj, ey);
    if (dot(xAxis, xAxis) == 0.0) {
        xAxis = cross(vj, ex);
    }
    xAxis = normalize(xAxis);
    vec3 yAxis = cross(xAxis, vj);
    yAxis = normalize(yAxis);

    // check j with all other neighbors k
    for (uint kCnt = 0; kCnt < numNeighbors; kCnt++) {
        // don't compare the circle with itself
        if (jIdx == kCnt) { 
            continue;
        }
        // the atom index of k
        //k = neighbors[atomIdx * params.maxNumNeighbors + kCnt];
        //uint k = neighbors[atomIdx * maxNumNeighbors + kCnt];
        uint k = texelFetch(neighborsTex, int(atomIdx * maxNumNeighbors + kCnt)).r; // PROFILE
        // get small circle k
        //vec4 sck = smallCircles[atomIdx * maxNumNeighbors + kCnt];
        vec4 sck = texelFetch(smallCirclesTex, int(atomIdx * maxNumNeighbors + kCnt)); // PROFILE
        // do nothing if small circle k has radius -1 (removed)
        if (sck.w < 0.0) {
            continue;
        }
        // vk = the small circle center
        vec3 vk = sck.xyz;
        // pk = center of atom k
        //vec4 ak = positions[k];
        vec4 ak = texelFetch(atomsTex, int(k)); // PROFILE
        vec3 pk = ak.xyz;
        // vj * vk
        float vjvk = dot(vj, vk);
        // denominator
        float denom = dot(vj, vj) * dot(vk, vk) - vjvk * vjvk;
        // point on straight line (intersection of small circle planes)
        vec3 h = vj * (dot(vj, vj - vk) * dot(vk, vk)) / denom + vk * (dot(vk - vj, vk) * dot(vj, vj)) / denom;
        // do nothing if h is outside of the extended sphere of atom i
        if (length(h) > R) { 
            continue;
        }
        // compute the root
        float root = sqrt((R * R - dot(h, h)) / dot(cross(vk, vj), cross(vk, vj)));
        // compute the two intersection points
        vec3 x1 = h + cross(vk, vj) * root;
        vec3 x2 = h - cross(vk, vj) * root;
        // swap x1 & x2 if vj points in the opposit direction of pj-pi
        if (dot(vk, pk - pi) < 0.0) {
            vec3 tmpVec = x1;
            x1 = x2;
            x2 = tmpVec;
        }
        // transform x1 and x2 to small circle coordinate system
        float xX1 = dot(x1 - vj, xAxis);
        float yX1 = dot(x1 - vj, yAxis);
        float xX2 = dot(x2 - vj, xAxis);
        float yX2 = dot(x2 - vj, yAxis);
        float angleX1 = atan(yX1, xX1);
        float angleX2 = atan(yX2, xX2);
        // limit angles to 0..2*PI
        /*if (angleX1 < 0.0) { HD6470 crash
            angleX1 += TWO_PI;
            angleX2 += TWO_PI;
        }*/
        //angleX1 = fmod(angleX1, TWO_PI); // HD6470 crash
        //angleX2 = fmod(angleX2, TWO_PI); // HD6470 crash
        if (angleX1 > TWO_PI) {
            angleX1 = fmod(angleX1, TWO_PI); // fmod
            angleX2 = fmod(angleX2, TWO_PI); // fmod
        }
        // angle of x2 has to be larger than angle of x1 (add 2 PI)
        if (angleX2 < angleX1) {
            angleX2 += TWO_PI;
        }
        // make all angles positive (add 2 PI)
        if (angleX1 < 0.0) {
            angleX1 += TWO_PI;
            angleX2 += TWO_PI;
        }

        // check all existing arcs with new arc k
        for (uint aCnt = 0; aCnt < arcCnt; aCnt++) {
            float s = start[globalIdx + aCnt];
            float e = end[aCnt];
            uint skIndex = startkIndex[aCnt];
            uint ekIndex = endkIndex[aCnt];
            if (arcValid[aCnt]) {
                if (angleX1 < s) {
                    // case (1) & (10)
                    if ((s - angleX1) > (angleX2 - angleX1)) {
                        if (((s - angleX1) + (e - s)) > TWO_PI) {
                            if (((s - angleX1) + (e - s)) < (TWO_PI + angleX2 - angleX1)) {
                                // case (10)
                                start[globalIdx + aCnt] = angleX1;
                                startkIndex[aCnt] = k;
                                end[aCnt] = fmod(e, TWO_PI); // fmod
                                // second angle check
                                if (end[aCnt] < start[globalIdx + aCnt]) {
                                    end[aCnt] += TWO_PI;
                                }
                            } else {
                                start[globalIdx + aCnt] = angleX1;
                                startkIndex[aCnt] = k;
                                end[aCnt] = fmod(angleX2, TWO_PI); // fmod
                                endkIndex[aCnt] = k;
                                // second angle check
                                if (end[aCnt] < start[globalIdx + aCnt]) {
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
                            end[aCnt] = fmod(angleX2, TWO_PI); // fmod
                            endkIndex[aCnt] = k;
                            // second angle check
                            if (end[aCnt] < start[globalIdx + aCnt]) {
                                end[aCnt] += TWO_PI;
                            }
                            if (((s - angleX1) + (e - s)) > TWO_PI) {
                                // case (6)
                                tmpStart[tmpArcCnt] = angleX1;
                                tmpStartkIndex[tmpArcCnt] = k;
                                tmpEnd[tmpArcCnt] = fmod(e, TWO_PI); // fmod
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
                                end[aCnt] = fmod(angleX2, TWO_PI); // fmod
                                endkIndex[aCnt] = k;
                                // second angle check
                                if (end[aCnt] < start[globalIdx + aCnt]) {
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
                            start[globalIdx + aCnt] = angleX1;
                            startkIndex[aCnt] = k;
                            // second angle check
                            end[aCnt] = fmod(end[aCnt], TWO_PI); // fmod
                            if (end[aCnt] < start[globalIdx + aCnt]) {
                                end[aCnt] += TWO_PI;
                            }
                            if (((angleX1 - s) + (angleX2 - angleX1)) > TWO_PI) {
                                // case (8)
                                tmpStart[tmpArcCnt] = s;
                                tmpStartkIndex[tmpArcCnt] = skIndex;
                                tmpEnd[tmpArcCnt] = fmod(angleX2, TWO_PI); // fmod
                                tmpEndkIndex[tmpArcCnt] = k;
                                // second angle check
                                if (tmpEnd[tmpArcCnt] < tmpStart[tmpArcCnt]) {
                                    tmpEnd[tmpArcCnt] += TWO_PI;
                                }
                                tmpArcCnt++;
                            }
                        } else {
                            // case (3)
                            start[globalIdx + aCnt] = angleX1;
                            startkIndex[aCnt] = k;
                            end[aCnt] = fmod(angleX2, TWO_PI); // fmod
                            endkIndex[aCnt] = k;
                            // second angle check
                            if (end[aCnt] < start[globalIdx + aCnt]) {
                                end[aCnt] += TWO_PI;
                            }
                        }
                    }
                }
            } // if (arcValid[aCnt])
        } // for (uint aCnt = 0; aCnt < arcCnt; aCnt++)

        // copy new arcs to arc array
        for (uint aCnt = 0; aCnt < tmpArcCnt; aCnt++) {
            start[globalIdx + aCnt + arcCnt] = tmpStart[aCnt];
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
        uint counter = 0;
        for (uint aCnt = 0; aCnt < arcCnt; aCnt++) {
            if (arcValid[aCnt]) {
                start[globalIdx + aCnt - counter] = start[globalIdx + aCnt];
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
    for (uint aCnt = 0; aCnt < arcCnt; aCnt++) {
        if (start[globalIdx + aCnt] < 0.00001) {
            idx0 = int(aCnt);
        } else if (abs(end[aCnt] - TWO_PI) < 0.0001) {
            idx2pi = int(aCnt);
        }
    }
    if (idx0 >= 0 && idx2pi >= 0) {
        start[globalIdx + uint(idx0)] = start[globalIdx + uint(idx2pi)];
        startkIndex[uint(idx0)] = startkIndex[uint(idx2pi)];
        // second angle check
        end[uint(idx0)] = fmod(end[uint(idx0)], TWO_PI); // fmod
        if (end[uint(idx0)] < start[globalIdx + uint(idx0)]) {
            end[uint(idx0)] += TWO_PI;
        }
        // fill gaps (overwrite removed arc idx2pi)
        for (uint aCnt = uint(idx2pi); aCnt < arcCnt - 1; aCnt++) {
            start[globalIdx + aCnt] = start[globalIdx + aCnt + 1];
            end[aCnt] = end[aCnt + 1];
            startkIndex[aCnt] = startkIndex[aCnt + 1];
            endkIndex[aCnt] = endkIndex[aCnt + 1];
            arcValid[aCnt] = true;
        }
        // subtract the removed arc from total number of arcs
        arcCnt--;
    }

    uint arcWritten = 0;
    // copy arcs to global arc array
    for (uint aCnt = 0; aCnt < arcCnt; aCnt++) {
        if (atomIdx < j) {
            uint k = startkIndex[aCnt];
            if (j < k) {
                uint index = atomicAdd(totalArcCount, 1);
                //uint index = atomicCounterIncrement(totalArcCount);
                //uint index = atomIdx * maxNumNeighbors * maxNumArcs + jIdx * maxNumArcs + arcWritten;
                arcs[index] = vec4(pi + vj + (cos(start[globalIdx + aCnt]) * xAxis + sin(start[globalIdx + aCnt]) * yAxis) * scj.w,
                        float(k) + 0.2); //start[aCnt]);
                writeArcHash(atomIdx, j, k, index, true);
                writeArcHash(atomIdx, k, j, index, false);
                writeArcHash(j, k, atomIdx, index, false);
                arcWritten++;
            }
            k = endkIndex[aCnt];
            if (j < k) {
                uint index = atomicAdd(totalArcCount, 1);
                //uint index = atomicCounterIncrement(totalArcCount);
                //uint index = atomIdx * maxNumNeighbors * maxNumArcs + jIdx * maxNumArcs + arcWritten;
                arcs[index] = vec4(pi + vj + (cos(end[aCnt]) * xAxis + sin(end[aCnt]) * yAxis) * scj.w,
                        float(k) + 0.2); //end[aCnt]);
                writeArcHash(atomIdx, j, k, index, true);
                writeArcHash(atomIdx, k, j, index, false);
                writeArcHash(j, k, atomIdx, index, false);
                arcWritten++;
            }
        }
    }

    // write number of arcs
    arcCount[atomIdx * maxNumNeighbors + jIdx] = arcWritten;

    // set small circle j visible if at least one arc was created and i < j
    if (arcCnt > 0) {
    //if( arcWritten > 0 ) {
        //if (atomIdx < j) {
            smallCirclesVisible[atomIdx * maxNumNeighbors + jIdx] = 1;
        //}
    }
    // DO NOT USE THIS!! It will create false, internal arcs!
    //if( arcCnt == 0 ) {
    //	smallCircles[atomIdx * params.maxNumNeighbors + jIdx].w = -1.0f;
    //}
    //atomicCounterIncrement(threadCount); // DEBUG
}

float fmod(float x, float y) {
    return x - y * trunc(x / y);
}

uint packArcIndex(uint atomIdx, uint index) {
    return atomIdx << 16 | index;
}

uint hash(uint value, uint iteration, uint capacity) {
    value = ((value ^ 131071) + 2039) % 131101;
    return (value + iteration * iteration) % capacity; // quadratic probing
}

void writeArcHash(uint i, uint j, uint k, uint arcIdx, bool primary) {
    uint key = i * atomsCount + j;
    uint keyFlags = primary ? key : (key | SECONDARY_FLAG);
    bool written = false;
    uint iteration = 0;
    uint maxIterations = min(maxHashIterations, maxNumTotalArcHashes);
    while (!written && iteration < maxIterations) {
        uint index = hash(key, iteration, maxNumTotalArcHashes);
        uint oldKey = atomicCompSwap(arcHashes[index].key, INVALID_KEY, keyFlags); // NVIDIA
        //uint oldKey = atomicCompSwap(arcHashes[index].key, keyFlags, INVALID_KEY); // ATI atomicCompSwap BUG (< 15.6 drivers)
        if (oldKey == INVALID_KEY) {
            arcHashes[index].atomk = k;
            arcHashes[index].index = arcIdx;
            arcHashes[index].padding = 0;
            written = true;
        }
        iteration++;
    }
    if (!written) {
        atomicAdd(hashErrorCount, 1);
        //atomicCounterIncrement(hashErrorCount);
    }
}