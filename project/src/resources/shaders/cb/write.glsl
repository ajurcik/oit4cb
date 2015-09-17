#version 430 core

struct triangle {
    vec4 probePos;
    vec4 atomVec1;
    vec4 atomVec2;
    vec4 atomVec3;
};

struct torus {
    vec4 position;
    vec4 axis;
    vec4 visibility;
    vec4 plane1;
    vec4 plane2;
};

const uint INVALID_KEY = 0xffffffff;
const uint INVALID_VALUE = 0xffffffff;
const uint SECONDARY_FLAG = 0x80000000;
const uint KEY_VALUE_MASK = 0x7fffffff;

const float TWO_PI = 6.28318530718;

uniform uint atomsCount;
uniform uint maxNumNeighbors;
uniform uint maxNumArcs;
uniform uint maxNumTotalArcHashes;
uniform uint maxHashIterations;

uniform samplerBuffer atomsTex;
uniform usamplerBuffer neighborsTex;
uniform usamplerBuffer neighborCountsTex;
uniform samplerBuffer smallCirclesTex;
uniform usamplerBuffer smallCircleVisibleTex;
uniform samplerBuffer arcsTex;
uniform usamplerBuffer arcCountsTex;
uniform usamplerBuffer arcHashesTex;

layout(std430) buffer Triangles {
    triangle triangles[];
};

layout(std430) buffer Tori {
    torus tori[];
};

layout(std430) buffer IsolatedTori {
    uint isolatedTori[];
};

layout(std430) buffer Edges {
    uvec4 edges[];
};

layout(std430) buffer EdgesCircle {
    vec4 edgesCircle[];
};

layout(std430) buffer EdgesLine {
    vec4 edgesLine[];
};

layout(std430) buffer CountersBuffer {
    uint toriCount;
    uint isolatedToriCount;
    uint hashErrorCount;
};

layout(std430) buffer Debug {
    vec4 debug[];
};

layout (local_size_x = 64) in;

uint hash(uint value, uint iteration, uint capacity);
uvec3 readArcHash(uint i, uint j, inout uint iteration);
uvec2 unpackArcIndex(uint value);

float angle(vec3 v, vec3 normal, vec3 origin);
vec4 torusPlane(vec3 pi, vec3 pj, uint k, vec3 probe);

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= atomsCount) {
        return;
    }

    vec4 ai = texelFetch(atomsTex, int(index));
    vec3 pi = ai.xyz;

    uint neighborCount = texelFetch(neighborCountsTex, int(index)).r;

    for (uint j = 0; j < neighborCount; j++) {
        uint jIdx = texelFetch(neighborsTex, int(index * maxNumNeighbors + j)).r;
        uint arcsCnt = texelFetch(arcCountsTex, int(index * maxNumNeighbors + j)).r;
        
        vec3 pj = texelFetch(atomsTex, int(jIdx)).rgb;

        // DEBUG
        //triangles[0].probePos = vec4(1.0, 2.0, float(neighborCount) + 0.1, float(arcsCnt) + 0.2);

        // mark visible atoms and write triangles
        uint iter = 0;
        for (uint a = 0; a < arcsCnt; a++) {
            uvec3 entry = readArcHash(index, jIdx, iter);
            while ((entry.r & SECONDARY_FLAG) > 0 && entry.r != INVALID_VALUE) {
                entry = readArcHash(index, jIdx, iter);
            }
            uint arcIdx = entry.z;
            //uint arcIdx = index * maxNumNeighbors * maxNumArcs + j * maxNumArcs + a;
            vec4 arc = texelFetch(arcsTex, int(arcIdx));
            uint k = uint(floor(arc.w));
            // mark atoms visible
            //atomsVisible[index] = 1;
            //atomsVisible[jIdx] = 1;
            //atomsVisible[k] = 1;
            // write triangle
            vec3 pk = texelFetch(atomsTex, int(k)).rgb;
            triangles[arcIdx].probePos = vec4(arc.xyz, float(arcIdx) + 0.2);
            triangles[arcIdx].atomVec1 = vec4(pi - arc.xyz, 1.0);
            triangles[arcIdx].atomVec2 = vec4(pj - arc.xyz, 1.0);
            triangles[arcIdx].atomVec3 = vec4(pk - arc.xyz, 1.0);
        }

        // write tori
        uint visible = texelFetch(smallCircleVisibleTex, int(index * maxNumNeighbors + j)).r;
        if (index < jIdx && visible > 0) {
            vec4 sc = texelFetch(smallCirclesTex, int(index * maxNumNeighbors + j));
            // torus axis
            vec3 ta = normalize(sc.xyz);
            // torus center
            vec3 tc = sc.xyz + pi;
            vec3 ortho = normalize(cross(ta, vec3(0.0, 0.0, 1.0)));

            // compute the tangential point X2 of the spheres
            vec3 P = tc + (ortho * sc.w);
            vec3 X = normalize(P - pi) * ai.w;
            vec3 C = (length(P - pi) / (length(P - pj) + length(P - pi))) * (pj - pi);
            float dist = length(X - C);
            C = (C + pi) - tc;

            // compute clipping planes
            vec4 arcs[17];
            uint arcIndices[17]; // surface graph support
            uint atoms[17];
            vec4 planes[17];
            uint count = 0;
            uint iteration = 0;
            uvec3 value = readArcHash(index, jIdx, iteration);
            while (value.x != INVALID_VALUE && count < 16) {
                arcs[count] = texelFetch(arcsTex, int(value.z));
                arcIndices[count] = value.z;
                atoms[count] = value.y;
                value = readArcHash(index, jIdx, iteration);
                count++;
            }

            if (count < 2) {
                /*debug[0] = index;
                debug[1] = jIdx;*/
                uint torusIdx = atomicAdd(toriCount, 1);
                uint isolatedTorusIdx = atomicAdd(isolatedToriCount, 1);
                float operation = 0.0; // ISOLATED
                // write torus center & torus radius R
                tori[torusIdx].position = vec4(tc, sc.w);
                // write torus axis & probe radius (= torus radius r)
                tori[torusIdx].axis = vec4(ta, operation);
                // write visibility sphere
                tori[torusIdx].visibility = vec4(C, dist);
                tori[torusIdx].plane1.x = uintBitsToFloat(index);
                tori[torusIdx].plane1.y = uintBitsToFloat(jIdx);
                tori[torusIdx].plane2 = vec4(1.0, 2.0, 3.0, 4.0); // DEBUG
                // write isolated torus index
                isolatedTori[isolatedTorusIdx] = torusIdx;
                // write invalid edge
                edges[torusIdx].x = INVALID_VALUE;
                edges[torusIdx].y = INVALID_VALUE;
            } else {
                vec3 orig = normalize(arcs[0].xyz - tc);
                for (uint a = 0; a < count - 1; a++) {
                    for (uint b = 0; b < count - a - 1; b++) {
                        vec3 prev = normalize(arcs[b].xyz - tc);
                        vec3 next = normalize(arcs[b+1].xyz - tc);
                        if (angle(prev, orig, ta) > angle(next, orig, ta)) {
                            vec4 tmpArc = arcs[b];
                            arcs[b] = arcs[b+1];
                            arcs[b+1] = tmpArc;
                            uint tmpArcIndex = arcIndices[b];
                            arcIndices[b] = arcIndices[b+1];
                            arcIndices[b+1] = tmpArcIndex;
                            uint tmpAtom = atoms[b];
                            atoms[b] = atoms[b+1];
                            atoms[b+1] = tmpAtom;
                        }
                    }
                }

                /*if (count > 2) {
                    debug[0] = vec4(tc, 0.0); // DEBUG
                    debug[1] = vec4(ta, 0.0); // DEBUG
                    debug[2] = vec4(orig, 0.0); // DEBUG
                    //atomicAdd(hashErrorCount, 1);
                }*/

                uint offset = 0;
                float totalAngle = 0.0;
                for (uint t = 0; t < count; t += 2) {
                    vec3 arc1 = arcs[offset + t].xyz;
                    vec3 arc2 = arcs[offset + t + 1].xyz;
                    vec3 arcVec1 = normalize(arc1 - tc);
                    vec3 arcVec2 = normalize(arc2 - tc);
                    vec4 plane1 = torusPlane(pi, pj, atoms[t], arc1);
                    planes[t] = plane1;
                    planes[t + 1] = torusPlane(pi, pj, atoms[t + 1], arc2);
                    if (dot(plane1.xyz, arc2) < -plane1.w) {
                        totalAngle += TWO_PI - acos(dot(arcVec1, arcVec2)); // OR
                    } else {
                        totalAngle += acos(dot(arcVec1, arcVec2)); // AND
                    }
                }
                if (totalAngle > TWO_PI) {
                    offset = 1;
                    arcs[count] = arcs[0];
                    arcIndices[count] = arcIndices[0];
                    atoms[count] = atoms[0];
                    planes[count] = planes[0];
                    //atomicAdd(hashErrorCount, 1); // DEBUG
                }
                
                // write torus
                for (uint t = 0; t < count; t += 2) {
                    uint torusIdx = atomicAdd(toriCount, 1);
                    vec4 arc1 = arcs[offset + t];
                    vec4 arc2 = arcs[offset + t + 1];
                    vec4 plane1 = planes[offset + t];
                    vec4 plane2 = planes[offset + t + 1];
                    float operation = 1.0; // AND
                    if (dot(plane1.xyz, arc2.xyz) < -plane1.w) {
                        operation = -1.0; // OR
                    }
                    /*if (index == k1 || jIdx == k1 || index == k2 || jIdx == k2) {
                        operation = 3.0; // DEBUG
                    }*/
                    /*if (count > 2) {
                        operation = 3.0; // DEBUG
                    }*/
                    // write torus center & torus radius R
                    tori[torusIdx].position = vec4(tc, sc.w);
                    // write torus axis & probe radius (= torus radius r)
                    tori[torusIdx].axis = vec4(ta, operation);
                    // write visibility sphere
                    tori[torusIdx].visibility = vec4(C, dist);
                    // write clipping planes
                    tori[torusIdx].plane1 = plane1;
                    tori[torusIdx].plane2 = plane2;
                    // write edge
                    edges[torusIdx].x = arcIndices[offset + t];
                    edges[torusIdx].y = arcIndices[offset + t + 1];
                    edges[torusIdx].z = index;
                    edges[torusIdx].w = jIdx;
                    // write circle
                    //edgesCircle[torusIdx] = vec4(tc, sc.w);
                    edgesCircle[torusIdx] = vec4(C + tc, dist);
                    // write line
                    vec3 center = operation * normalize(arc1.xyz + arc2.xyz - 2 * tc);
                    edgesLine[torusIdx].xyz = center;
                    edgesLine[torusIdx].w = dot(normalize(arc1.xyz - tc), center);
                    /*if (count > 2) {
                        debug[t + 3] = vec4(sc.w * normalize(arc1.xyz - tc), angle(normalize(arc1.xyz - tc), orig, ta)); // DEBUG
                        debug[t + 4] = vec4(sc.w * normalize(arc2.xyz - tc), angle(normalize(arc2.xyz - tc), orig, ta)); // DEBUG
                    }*/
                }
            }
        }
    }
}

float angle(vec3 v, vec3 o, vec3 normal) {
    float signum = dot(cross(o, v), normal);
    float cosin = dot(o, v);
    if (signum == 0.0) {
        return min(cosin, 0.0);
    } else if (signum > 0.0) {
        return -0.5 * cosin + 0.5;
    } else /* signum < 0.0 */ {
        return 0.5 * cosin - 0.5;
    }
}

vec4 torusPlane(vec3 pi, vec3 pj, uint k, vec3 probe) {
    vec3 pk = texelFetch(atomsTex, int(k)).rgb;
    vec3 vi = pi - probe;
    vec3 vj = pj - probe;
    vec3 n = normalize(cross(vi, vj));
    float d = -dot(n, probe);
    float side = dot(n, pk) + d;
    if (side > 0.0) {
        return vec4(-n, -d);
    } else /* side < 0.0 */ {
        return vec4(n, d);
    }
}

uvec3 readArcHash(uint i, uint j, inout uint iteration) {
    uint key = i * atomsCount + j;
    uvec3 value = uvec3(INVALID_VALUE);
    bool read = false;
    uint maxIterations = min(maxHashIterations, maxNumTotalArcHashes);
    while (!read && iteration < maxIterations) {
        uint index = hash(key, iteration, maxNumTotalArcHashes);
        uvec3 entry = texelFetch(arcHashesTex, int(index)).rgb;
        if ((entry.x & KEY_VALUE_MASK) == key) {
            value = entry;
            read = true;
        } else if (entry.x == INVALID_KEY) {
            break;
        }
        iteration++;
    }
    return value;
}

uint hash(uint value, uint iteration, uint capacity) {
    value = ((value ^ 131071) + 2039) % 131101;
    return (value + iteration * iteration) % capacity; // quadratic probing
}

uvec2 unpackArcIndex(uint value) {
    return uvec2(0x0000ffff & value, value >> 16);
}