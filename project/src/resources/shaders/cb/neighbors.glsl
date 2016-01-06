#version 430 core

uniform uint gridSize;
uniform float cellSize;

uniform uint sphereCount;
uniform uint maxNumSpheres;
uniform uint maxNumNeighbors;

uniform float probeRadius;

uniform samplerBuffer spheresTex;
uniform usamplerBuffer gridCountsTex;
uniform usamplerBuffer gridIndicesTex;

layout(std430) buffer Neighbors {
    uint neighbors[];
};

layout(std430) buffer NeighborCounts {
    uint neighborCounts[];
};

layout(std430) buffer SmallCircles {
    vec4 smallCircles[];
};

layout(local_size_x = 64) in;

uint findNeighborsInCell(uint neighborIndex, uvec3 gridPos, uint index, vec4 pos);

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= sphereCount) {
        return;
    }

    vec4 pos = texelFetch(spheresTex, int(index));
    ivec3 gridPos = ivec3(floor(pos.xyz / cellSize));

    float range = pos.w + 3.0 + 2.0 * probeRadius;
    // compute number of grid cells
    ivec3 cellsInRange;
    cellsInRange.x = int(ceil(range / cellSize));
    cellsInRange.y = int(ceil(range / cellSize));
    cellsInRange.z = int(ceil(range / cellSize));
    uvec3 start = uvec3(max(gridPos - cellsInRange, ivec3(0)));
    uvec3 end = uvec3(min(gridPos + cellsInRange, ivec3(gridSize - 1)));

    // examine neighbouring cells
    uint count = 0;
    uvec3 neighborPos;
    for (uint z = start.z; z <= end.z; z++) {
        for (uint y = start.y; y <= end.y; y++) {
            for (uint x = start.x; x <= end.x; x++) {
                neighborPos = uvec3(x, y, z);
                count += findNeighborsInCell(count, neighborPos, index, pos);
            }
        }
    }

    // write new neighbor atom count back to (sorted) index location
    neighborCounts[index] = count;
}

uint findNeighborsInCell(uint neighborIndex, uvec3 gridPos, uint index, vec4 pos) {
    uint hash = gridSize * gridSize * gridPos.x + gridSize * gridPos.y + gridPos.z;
    uint spheresInCell = texelFetch(gridCountsTex, int(hash)).r;
    uint count = 0;
    // iterate over spheres in this cell
    for (uint j = 0; j < spheresInCell; j++) {
        uint index2 = texelFetch(gridIndicesTex, int(maxNumSpheres * hash + j)).r;
        // do not count self
        if (index2 != index) {
            // get position of potential neighbor
            vec4 pos2 = texelFetch(spheresTex, int(index2));
            // check distance
            vec3 relPos = pos2.xyz - pos.xyz;
            float dist = length(relPos);
            float neighborDist = pos.w + pos2.w + 2.0 * probeRadius;
            if (dist < neighborDist) {
                // check number of neighbors
                if ((neighborIndex + count) >= maxNumNeighbors) {
                    return count;
                }
                // write the (sorted) neighbor index
                neighbors[index * maxNumNeighbors + neighborIndex + count] = index2;
                // compute small circle / intersection plane
                float r = ((pos.w + probeRadius) * (pos.w + probeRadius))
                    + (dist * dist)
                    - ((pos2.w + probeRadius) * (pos2.w + probeRadius));
                r = r / (2.0 * dist * dist);
                vec3 vec = relPos * r;
                // set small circle
                vec4 smallCircle;
                smallCircle.xyz = vec;
                smallCircle.w = sign(r) * sqrt(((pos.w + probeRadius) * (pos.w + probeRadius)) - dot(vec, vec));
                smallCircles[index * maxNumNeighbors + neighborIndex + count] = smallCircle;
                // increment the neighbor counter
                count++;
            }
        }
    }
    return count;
}