#version 430 core

uniform uint gridSize;
uniform float cellSize;

uniform uint probeCount;
uniform uint maxNumSpheres;
uniform uint maxNumNeighbors;

uniform float probeRadius;

uniform uint surfaceLabel;
uniform float areaThreshold;

uniform samplerBuffer probesTex;
uniform usamplerBuffer gridCountsTex;
uniform usamplerBuffer gridIndicesTex;
uniform usamplerBuffer labelsTex;
uniform sampler1D areasTex;

/*layout(std430) buffer Neighbors {
    uint neighbors[];
};*/

layout(std430) buffer NeighborCounts {
    uint neighborCounts[];
};

layout(std430) buffer NeighborProbes {
    vec4 neighborProbes[];
};

layout(local_size_x = 64) in;

uint findNeighborsInCell(uint neighborIndex, uvec3 gridPos, uint index, uint label, vec3 pos);

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= probeCount) {
        return;
    }

    vec3 pos = texelFetch(probesTex, int(index)).rgb;
    ivec3 gridPos = ivec3(floor(pos.xyz / cellSize));

    uint label = texelFetch(labelsTex, int(index)).r;

    float range = 2.0 * probeRadius;
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
                count += findNeighborsInCell(count, neighborPos, index, label, pos);
            }
        }
    }

    // write new neighbor atom count back to (sorted) index location
    neighborCounts[index] = count;
}

uint findNeighborsInCell(uint neighborIndex, uvec3 gridPos, uint index, uint label, vec3 pos) {
    uint hash = gridSize * gridSize * gridPos.x + gridSize * gridPos.y + gridPos.z;
    uint spheresInCell = texelFetch(gridCountsTex, int(hash)).r;
    uint count = 0;
    // iterate over spheres in this cell
    for (uint j = 0; j < spheresInCell; j++) {
        uint index2 = texelFetch(gridIndicesTex, int(maxNumSpheres * hash + j)).r;
        // do not count self
        if (index2 != index) {
            // get position of potential neighbor
            vec3 pos2 = texelFetch(probesTex, int(index2)).rgb;
            // check distance
            vec3 relPos = pos2.xyz - pos.xyz;
            float dist = length(relPos);
            float neighborDist = 2.0 * probeRadius;
            if (dist < neighborDist) {
                // check label (cavities should not clip surface)
                uint label2 = texelFetch(labelsTex, int(index2)).r;
                if (label == surfaceLabel && label2 != surfaceLabel) {
                    continue;
                }
                // check area (hidden cavities should not clip other cavities)
                float area = texelFetch(areasTex, int(label2) - 1, 0).r;
                if (area < areaThreshold) {
                    continue;
                }
                // check number of neighbors
                if ((neighborIndex + count) >= maxNumNeighbors) {
                    return count;
                }
                // write the (sorted) neighbor index
                //neighbors[index * maxNumNeighbors + neighborIndex + count] = index2;
                // write neighbor probe
                neighborProbes[index * maxNumNeighbors + neighborIndex + count] = vec4(pos2, 1.0);
                // increment the neighbor counter
                count++;
            }
        }
    }
    return count;
}