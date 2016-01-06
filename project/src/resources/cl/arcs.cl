typedef struct {
    uint key;
    uint atomk;
    uint index;
    uint padding;
} arc_entry;

constant uint INVALID_KEY = 0xffffffff;
constant uint SECONDARY_FLAG = 0x80000000;
constant uint KEY_VALUE_MASK = 0x07ffffff;

constant float TWO_PI = 6.28318530718f;

typedef struct {
    uint threadCount;
    uint hashCount;
    uint hashErrorCount;
    uint totalArcCount;
} counters_t;

typedef struct {
    int atomsCount;
    int maxNumNeighbors;
    int maxNumTotalArcHashes;
    int maxHashIterations;
    float probeRadius;
} params_t;

//float fmod(float x, float y);
uint packArcIndex(uint atomIdx, uint index);
void writeArcHash(uint i, uint j, uint k, uint index, bool primary,
        global arc_entry * arcHashes, global counters_t * counters, constant params_t * params);

kernel void arcs(
            /*read_only image1d_t*/ global const float4 * atoms, // float4
            /*read_only image1d_t*/ global const uint * neighbors, // uint
            /*read_only image1d_t*/ global const uint * neighborCounts, // uint
            /*read_only image1d_t*/ global const float4 * smallCircles, // float4
            global float4 * arcs,
            global uint * arcCount,
            global arc_entry * arcHashes,
            global uint * smallCirclesVisible,
            global counters_t * counters,
            constant params_t * params) {
    // get atom index
    uint atomIdx = get_global_id(1);
    // get neighbor atom index
    uint jIdx = get_global_id(0);
    // check, if atom index is within bounds
    if (atomIdx >= params->atomsCount) return;
    // check, if neighbor index is within bounds
    if (jIdx >= params->maxNumNeighbors) return;
    // check, if neighbor index is within bounds
    uint numNeighbors = neighborCounts[atomIdx];
    //uint numNeighbors = read_imageui(neighborCounts, sampler, (int2)(atomIdx, 0)).x; // PROFILE
    if (jIdx >= numNeighbors) return;
    
    // read position and radius of atom i from sorted array
    float4 atomi = atoms[atomIdx];
    //float4 atomi = read_imagef(atoms, sampler, (int2)(atomIdx, 0)); // PROFILE
    float3 pi = atomi.xyz;
    float R = atomi.w + params->probeRadius;

    // the atom index of j
    //uint j = neighbors[atomIdx * params.maxNumNeighbors + jIdx];
    uint j = neighbors[atomIdx * params->maxNumNeighbors + jIdx];
    //uint j = read_imageui(neighbors, sampler, (int2)(atomIdx * maxNumNeighbors + jIdx)).x; // PROFILE
    // get small circle j
    float4 scj = smallCircles[atomIdx * params->maxNumNeighbors + jIdx];
    //float4 scj = read_imagef(smallCircles, sampler, (int2)(atomIdx * maxNumNeighbors + jIdx)); // PROFILE
    // do nothing if small circle j has radius -1 (removed)
    if (scj.w < -10.0f) {
        //arcCount[atomIdx * maxNumNeighbors + jIdx] = 0; // DEBUG
        return;
    }
    scj.w = fabs(scj.w);
    //atomicCounterIncrement(threadCount); // DEBUG
    // vj = the small circle center
    float3 vj = scj.xyz;
    // pj = center of atom j
    //float4 aj = atoms[j]; // NOT REFERENCED?
    //float4 aj = read_imagef(atoms, sampler, (int2)(j)); // PROFILE
    //float3 pj = aj.xyz; // NOT REFERENCED?
    // store all arcs
    float start[64];
    float end[64];
    uint startkIndex[64];
    uint endkIndex[64];
    bool arcValid[64];
    start[0] = 0.0f;
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
    float3 ex = (float3)(1.0f, 0.0f, 0.0f);
    float3 ey = (float3)(0.0f, 1.0f, 0.0f);
    float3 xAxis = cross(vj, ey);
    if (dot(xAxis, xAxis) == 0.0f) {
        xAxis = cross(vj, ex);
    }
    xAxis = normalize(xAxis);
    float3 yAxis = cross(xAxis, vj);
    yAxis = normalize(yAxis);

    // check j with all other neighbors k
    for (uint kCnt = 0; kCnt < numNeighbors; kCnt++) {
        // don't compare the circle with itself
        if (jIdx == kCnt) { 
            continue;
        }
        // the atom index of k
        //k = neighbors[atomIdx * params.maxNumNeighbors + kCnt];
        uint k = neighbors[atomIdx * params->maxNumNeighbors + kCnt];
        //uint k = read_imageui(neighbors, sampler, (int2)(atomIdx * maxNumNeighbors + kCnt)).x; // PROFILE
        // get small circle k
        float4 sck = smallCircles[atomIdx * params->maxNumNeighbors + kCnt];
        //float4 sck = read_imagef(smallCircles, sampler, (int2)(atomIdx * maxNumNeighbors + kCnt)); // PROFILE
        // do nothing if small circle k has radius -1 (removed)
        if (sck.w < -10.0f) {
            continue;
        }
        sck.w = fabs(sck.w);
        // vk = the small circle center
        float3 vk = sck.xyz;
        // pk = center of atom k
        float4 ak = atoms[k];
        //float4 ak = read_imagef(atoms, sampler, (int2)(k)); // PROFILE
        float3 pk = ak.xyz;
        // vj * vk
        float vjvk = dot(vj, vk);
        // denominator
        float denom = dot(vj, vj) * dot(vk, vk) - vjvk * vjvk;
        // point on straight line (intersection of small circle planes)
        float3 h = vj * (dot(vj, vj - vk) * dot(vk, vk)) / denom + vk * (dot(vk - vj, vk) * dot(vj, vj)) / denom;
        // do nothing if h is outside of the extended sphere of atom i
        if (length(h) > R) { 
            continue;
        }
        // compute the root
        float root = sqrt((R * R - dot(h, h)) / dot(cross(vk, vj), cross(vk, vj)));
        // compute the two intersection points
        float3 x1 = h + cross(vk, vj) * root;
        float3 x2 = h - cross(vk, vj) * root;
        // swap x1 & x2 if vj points in the opposit direction of pj-pi
        if (dot(vk, pk - pi) < 0.0f) {
            float3 tmpVec = x1;
            x1 = x2;
            x2 = tmpVec;
        }
        // transform x1 and x2 to small circle coordinate system
        float xX1 = dot(x1 - vj, xAxis);
        float yX1 = dot(x1 - vj, yAxis);
        float xX2 = dot(x2 - vj, xAxis);
        float yX2 = dot(x2 - vj, yAxis);
        float angleX1 = atan2(yX1, xX1);
        float angleX2 = atan2(yX2, xX2);
        // limit angles to 0..2*PI
        if (angleX1 > TWO_PI) {
            angleX1 = fmod(angleX1, TWO_PI);
            angleX2 = fmod(angleX2, TWO_PI);
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
        for (uint aCnt = 0; aCnt < arcCnt; aCnt++) {
            float s = start[aCnt];
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
                                start[aCnt] = angleX1;
                                startkIndex[aCnt] = k;
                                end[aCnt] = fmod(e, TWO_PI); // fmod
                                // second angle check
                                if (end[aCnt] < start[aCnt]) {
                                    end[aCnt] += TWO_PI;
                                }
                            } else {
                                start[aCnt] = angleX1;
                                startkIndex[aCnt] = k;
                                end[aCnt] = fmod(angleX2, TWO_PI); // fmod
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
                            end[aCnt] = fmod(angleX2, TWO_PI); // fmod
                            endkIndex[aCnt] = k;
                            // second angle check
                            if (end[aCnt] < start[aCnt]) {
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
                            end[aCnt] = fmod(end[aCnt], TWO_PI); // fmod
                            if (end[aCnt] < start[aCnt]) {
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
                            start[aCnt] = angleX1;
                            startkIndex[aCnt] = k;
                            end[aCnt] = fmod(angleX2, TWO_PI); // fmod
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
        if (tmpArcCnt > 16 || arcCnt > 32) {
            counters->hashErrorCount = tmpArcCnt * 1000;
        }
        if (arcCnt > 32) {
            counters->hashErrorCount = arcCnt * 1000;
        }

        // copy new arcs to arc array
        for (uint aCnt = 0; aCnt < tmpArcCnt; aCnt++) {
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
        uint counter = 0;
        for (uint aCnt = 0; aCnt < arcCnt; aCnt++) {
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
    for (uint aCnt = 0; aCnt < arcCnt; aCnt++) {
        if (start[aCnt] < 0.00001f) {
            idx0 = (int)aCnt;
        } else if (fabs(end[aCnt] - TWO_PI) < 0.0001f) {
            idx2pi = (int)aCnt;
        }
    }
    if (idx0 >= 0 && idx2pi >= 0) {
        start[(uint)idx0] = start[(uint)idx2pi];
        startkIndex[(uint)idx0] = startkIndex[(uint)idx2pi];
        // second angle check
        end[(uint)idx0] = fmod(end[(uint)idx0], TWO_PI); // fmod
        if (end[(uint)idx0] < start[(uint)idx0]) {
            end[(uint)idx0] += TWO_PI;
        }
        // fill gaps (overwrite removed arc idx2pi)
        for (uint aCnt = (uint)idx2pi; aCnt < arcCnt - 1; aCnt++) {
            start[aCnt] = start[aCnt + 1];
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
                uint index = atomic_add(&counters->totalArcCount, 1);
                //uint index = atomicCounterIncrement(totalArcCount);
                //uint index = atomIdx * maxNumNeighbors * maxNumArcs + jIdx * maxNumArcs + arcWritten;
                arcs[index] = (float4)(pi + vj + (cos(start[aCnt]) * xAxis + sin(start[aCnt]) * yAxis) * scj.w,
                        (float)k + 0.2f); //start[aCnt]);
                writeArcHash(atomIdx, j, k, index, true, arcHashes, counters, params);
                writeArcHash(atomIdx, k, j, index, false, arcHashes, counters, params);
                writeArcHash(j, k, atomIdx, index, false, arcHashes, counters, params);
                arcWritten++;
            }
            k = endkIndex[aCnt];
            if (j < k) {
                uint index = atomic_add(&counters->totalArcCount, 1);
                //uint index = atomicCounterIncrement(totalArcCount);
                //uint index = atomIdx * maxNumNeighbors * maxNumArcs + jIdx * maxNumArcs + arcWritten;
                arcs[index] = (float4)(pi + vj + (cos(end[aCnt]) * xAxis + sin(end[aCnt]) * yAxis) * scj.w,
                        (float)k + 0.2f); //end[aCnt]);
                writeArcHash(atomIdx, j, k, index, true, arcHashes, counters, params);
                writeArcHash(atomIdx, k, j, index, false, arcHashes, counters, params);
                writeArcHash(j, k, atomIdx, index, false, arcHashes, counters, params);
                arcWritten++;
            }
        }
    }

    // write number of arcs
    arcCount[atomIdx * params->maxNumNeighbors + jIdx] = arcWritten;

    // set small circle j visible if at least one arc was created and i < j
    if (arcCnt > 0) {
    //if( arcWritten > 0 ) {
        //if (atomIdx < j) {
            smallCirclesVisible[atomIdx * params->maxNumNeighbors + jIdx] = 1;
        //}
    }
    // DO NOT USE THIS!! It will create false, internal arcs!
    //if( arcCnt == 0 ) {
    //	smallCircles[atomIdx * params.maxNumNeighbors + jIdx].w = -1.0f;
    //}
    //atomicCounterIncrement(threadCount); // DEBUG
}

/*float fmod(float x, float y) {
    return x - y * trunc(x / y);
}*/

uint packArcIndex(uint atomIdx, uint index) {
    return atomIdx << 16 | index;
}

uint hash(uint value, uint iteration, uint capacity) {
    value = ((value ^ 131071) + 2039) % 131101;
    return (value + iteration * iteration) % capacity; // quadratic probing
}

void writeArcHash(uint i, uint j, uint k, uint arcIdx, bool primary,
        global arc_entry * arcHashes,
        global counters_t * counters,
        constant params_t * params) {
    uint key = i * params->atomsCount + j;
    uint keyFlags = primary ? key : (key | SECONDARY_FLAG);
    bool written = false;
    uint iteration = 0;
    uint maxIterations = min(params->maxHashIterations, params->maxNumTotalArcHashes);
    while (!written && iteration < maxIterations) {
        uint index = hash(key, iteration, params->maxNumTotalArcHashes);
        uint oldKey = atomic_cmpxchg(&arcHashes[index].key, INVALID_KEY, keyFlags);
        if (oldKey == INVALID_KEY) {
            arcHashes[index].atomk = k;
            arcHashes[index].index = arcIdx;
            written = true;
        }
        iteration++;
    }
    if (!written) {
        atomic_add(&counters->hashErrorCount, 1);
    }
}