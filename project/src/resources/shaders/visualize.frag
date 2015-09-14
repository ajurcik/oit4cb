#version 430

uniform uint atomsCount;

in flat uvec3 index;
out vec4 fragColor;

layout(std430) buffer Counts {
    uint counts[];
};

layout(std430) buffer NeighborCounts {
    uint neighborCounts[];
};

void main() {
    uint atom = 256*index.x + 16*index.y + index.z;
    if (atom >= atomsCount) {
        discard;
    }
    float count = float(neighborCounts[atom]) / 1.0;
    //float count = float(counts[256*index.x + 16*index.y + index.z]) / 4.0;
    fragColor.rgb = vec3(0.0, min(count, 1.0), 0.0);
    fragColor.a = 1.0;
}