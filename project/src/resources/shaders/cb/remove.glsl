#version 430 core

uniform uint atomsCount;
uniform uint maxNumNeighbors;

uniform float probeRadius;

uniform samplerBuffer atomsTex;
uniform usamplerBuffer neighborsTex;
uniform usamplerBuffer neighborCountsTex;
uniform samplerBuffer smallCirclesTex;

layout(std430) buffer NeighborCounts {
    uint neighborCounts[];
};

layout(std430) buffer SmallCircles {
    vec4 smallCircles[];
};

layout(std430) buffer SmallCirclesVisible {
    uint smallCirclesVisible[];
};

layout (local_size_x = 64, local_size_y = 2) in;

void main() {
    // get atom index
    uint atomIdx = gl_GlobalInvocationID.y;
    // get neighbor atom index
    uint jIdx = gl_WorkGroupID.x * gl_WorkGroupSize.x + gl_LocalInvocationID.x;
    // check, if atom index is within bounds
    if (atomIdx >= atomsCount) return;
    // check, if neighbor index is within bounds
    if (jIdx >= maxNumNeighbors) return;
    // set small circle visibility to false
    smallCirclesVisible[atomIdx * maxNumNeighbors + jIdx] = 0;
    // check, if neighbor index is within bounds
    //uint numNeighbors = neighborCounts[atomIdx];
    uint numNeighbors = texelFetch(neighborCountsTex, int(atomIdx)).r;
    if (jIdx >= numNeighbors) return;

    // read position and radius of atom i from sorted array
    //vec4 atomi = positions[atomIdx];
    vec4 atomi = texelFetch(atomsTex, int(atomIdx));
    vec3 pi = atomi.xyz;
    float R = atomi.w + probeRadius;

    // flag wether j should be added (true) is cut off (false)
    bool addJ = true;

    // the atom index of j
    //uint j = neighbors[atomIdx * maxNumNeighbors + jIdx];
    uint j = texelFetch(neighborsTex, int(atomIdx * maxNumNeighbors + jIdx)).r;
    // get small circle j
    //vec4 scj = smallCircles[atomIdx * maxNumNeighbors + jIdx];
    vec4 scj = texelFetch(smallCirclesTex, int(atomIdx * maxNumNeighbors + jIdx));
    // vj = the small circle center
    vec3 vj = scj.xyz;
    // pj = center of atom j
    //vec4 aj = positions[j];
    vec4 aj = texelFetch(atomsTex, int(j));
    vec3 pj = aj.xyz;

    // check j with all other neighbors k
    for (uint kCnt = 0; kCnt < numNeighbors; kCnt++) {
        // don't compare the circle with itself
        if (jIdx != kCnt) {
            // the atom index of k
            //uint k = neighbors[atomIdx * maxNumNeighbors + kCnt];
            uint k = texelFetch(neighborsTex, int(atomIdx * maxNumNeighbors + kCnt)).r;
            // pk = center of atom k
            //vec4 ak = positions[k];
            vec4 ak = texelFetch(atomsTex, int(k));
            vec3 pk = ak.xyz;
            // get small circle k
            //vec4 sck = smallCircles[atomIdx * maxNumNeighbors + kCnt];
            vec4 sck = texelFetch(smallCirclesTex, int(atomIdx * maxNumNeighbors + kCnt));
            // vk = the small circle center
            vec3 vk = sck.xyz;
            // vj * vk
            float vjvk = dot(vj, vk);
            // denominator
            float denom = dot(vj, vj) * dot(vk, vk) - vjvk * vjvk;
            // point on straight line (intersection of small circle planes)
            vec3 h = vj * (dot(vj, vj - vk) * dot(vk, vk)) / denom + vk * (dot(vk - vj, vk) * dot(vj, vj)) / denom;
            // compute cases
            vec3 nj = normalize(pi - pj);
            vec3 nk = normalize(pi - pk);
            vec3 q = vk - vj;
            // if normals are the same (unrealistic, yet theoretically possible)
            if (dot(nj, nk) == 1.0) {
                if (dot(nj, nk) > 0.0 /*Redundant?*/) {
                    if (dot(nj, q) > 0.0) {
                        // k cuts off j --> remove j
                        addJ = false;
                    }
                }
            } else if (length(h) > R) {
                vec3 mj = (vj - h);
                vec3 mk = (vk - h);
                if (dot(nj, nk) > 0.0) {
                    if (dot(mj, mk) > 0.0 && dot(nj, q) > 0.0) {
                        // k cuts off j --> remove j
                        addJ = false;
                    }
                } else {
                    if (dot(mj, mk) > 0.0 && dot(nj, q) < 0.0) {
                        // atom i has no contour
                        neighborCounts[atomIdx] = 0;
                    }
                }
            }
        }
    }
    // all k were tested, see if j is cut off
    if (!addJ) {
        smallCircles[atomIdx * maxNumNeighbors + jIdx].w = -11.0;
    }
}
