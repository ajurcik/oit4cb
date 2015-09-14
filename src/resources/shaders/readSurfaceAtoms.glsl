#version 430

#extension GL_ARB_compute_shader : require
#extension GL_ARB_shader_storage_buffer_object : require

uniform uint atomsCount;
uniform uint maxNumNeighbors;
uniform uint maxNumArcs;

layout(std430) buffer NeighborCounts {
    uint neighborCounts[];
};

layout(std430) buffer Neighbors {
    uint neighbors[];
};

layout(std430) buffer ArcsCounts {
    uint arcsCount[];
};

layout(std430) buffer Arcs {
    vec4 arcs[];
};

layout(std430) buffer AtomsVisible {
    uint atomsVisible[];
};

layout (local_size_x = 64) in;

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= atomsCount) {
        return;
    }

    uint neighborCount = neighborCounts[index];

    for (uint j = 0; j < neighborCount; j++) {
        uint arcsCnt = arcsCount[index * maxNumNeighbors + j];
        for (uint a = 0; a < arcsCnt; a++) {
            vec4 arc = arcs[index * maxNumNeighbors * maxNumArcs + j * maxNumArcs + a];
            uint k = uint(floor(arc.w));
            // mark atoms visible
            atomsVisible[index] = 1;
            atomsVisible[neighbors[index * maxNumNeighbors + j]] = 1;
            atomsVisible[k] = 1;
        }
    }
}